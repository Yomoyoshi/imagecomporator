package com.image.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.hamcrest.Matchers.is;

@WebMvcTest(FileController.class)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @TempDir
    Path tempDir;

    private File validImageFile;
    private File nonImageFile;
    private File nonExistentFile;

    @BeforeEach
    void setUp() throws IOException {
        // Создаем валидный файл с расширением .jpg
        validImageFile = tempDir.resolve("test.jpg").toFile();
        Files.write(validImageFile.toPath(), new byte[1024]);

        // Создаем текстовый файл
        nonImageFile = tempDir.resolve("test.txt").toFile();
        Files.writeString(nonImageFile.toPath(), "some text");

        // Несуществующий файл
        nonExistentFile = tempDir.resolve("notexist.jpg").toFile();
    }

    @Test
    void getImage_withExistingImage_shouldReturnOk() throws Exception {
        String encodedPath = URLEncoder.encode(validImageFile.getAbsolutePath(), StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/files/image")
                        .param("path", encodedPath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Content-Length", String.valueOf(validImageFile.length())));
    }

    @Test
    void getImage_withNonExistentFile_shouldReturnNotFound() throws Exception {
        String encodedPath = URLEncoder.encode(nonExistentFile.getAbsolutePath(), StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/files/image")
                        .param("path", encodedPath))
                .andExpect(status().isNotFound());
    }

    @Test
    void getImage_withExistingNonImageFile_shouldReturnOkWithDetectedMime() throws Exception {
        String encodedPath = URLEncoder.encode(nonImageFile.getAbsolutePath(), StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/files/image")
                        .param("path", encodedPath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(header().string("Content-Length", String.valueOf(nonImageFile.length())));
    }

    @Test
    void getFileInfo_withExistingFile_shouldReturnInfo() throws Exception {
        String encodedPath = URLEncoder.encode(validImageFile.getAbsolutePath(), StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/files/info")
                        .param("path", encodedPath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value(validImageFile.getAbsolutePath()))
                .andExpect(jsonPath("$.filename").value(validImageFile.getName()))
                .andExpect(jsonPath("$.size").value(validImageFile.length()))
                .andExpect(jsonPath("$.lastModified").exists());
    }

    @Test
    void getFileInfo_withNonExistentFile_shouldReturnNotFound() throws Exception {
        String encodedPath = URLEncoder.encode(nonExistentFile.getAbsolutePath(), StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/files/info")
                        .param("path", encodedPath))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteFile_withExistingFile_shouldReturnSuccess() throws Exception {
        File fileToDelete = tempDir.resolve("toDelete.jpg").toFile();
        Files.write(fileToDelete.toPath(), new byte[1024]);
        String encodedPath = URLEncoder.encode(fileToDelete.getAbsolutePath(), StandardCharsets.UTF_8);

        mockMvc.perform(delete("/api/files/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"" + encodedPath + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("File deleted"));

        assert !fileToDelete.exists();
    }

    @Test
    void deleteFile_withNonExistentFile_shouldReturnNotFound() throws Exception {
        String encodedPath = URLEncoder.encode(nonExistentFile.getAbsolutePath(), StandardCharsets.UTF_8);

        mockMvc.perform(delete("/api/files/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"" + encodedPath + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteFile_withReadOnlyFile_shouldReturnError() throws Exception {
        // Создаём файл, который нельзя удалить (делаем его только для чтения)
        File readOnlyFile = tempDir.resolve("readonly.jpg").toFile();
        Files.write(readOnlyFile.toPath(), new byte[100]);
        readOnlyFile.setReadOnly();

        assumeTrue(!System.getProperty("os.name").toLowerCase().contains("win"));

        if (!readOnlyFile.canWrite()) {
            String encodedPath = URLEncoder.encode(readOnlyFile.getAbsolutePath(), StandardCharsets.UTF_8);

            mockMvc.perform(delete("/api/files/delete")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"path\":\"" + encodedPath + "\"}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status", is("error")));
        }
    }

    @Test
    void getImage_withJpgFile_shouldFallbackToJpegMimeWhenProbeFails() throws Exception {
        File jpgFile = tempDir.resolve("test.jpg").toFile();
        Files.write(jpgFile.toPath(), new byte[10]);
        String encodedPath = URLEncoder.encode(jpgFile.getAbsolutePath(), StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/files/image")
                        .param("path", encodedPath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Content-Length", String.valueOf(jpgFile.length())));
    }

    @Test
    void getImage_withJpegFile_shouldFallbackToJpegMime() throws Exception {
        File jpegFile = tempDir.resolve("test.jpeg").toFile();
        Files.write(jpegFile.toPath(), new byte[10]);
        String encodedPath = URLEncoder.encode(jpegFile.getAbsolutePath(), StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/files/image")
                        .param("path", encodedPath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG));
    }

    @Test
    void getImage_withPngFile_shouldFallbackToPngMime() throws Exception {
        File pngFile = tempDir.resolve("test.png").toFile();
        Files.write(pngFile.toPath(), new byte[10]);
        String encodedPath = URLEncoder.encode(pngFile.getAbsolutePath(), StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/files/image")
                        .param("path", encodedPath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));
    }

    @Test
    void getImage_withGifFile_shouldFallbackToGifMime() throws Exception {
        File gifFile = tempDir.resolve("test.gif").toFile();
        Files.write(gifFile.toPath(), new byte[10]);
        String encodedPath = URLEncoder.encode(gifFile.getAbsolutePath(), StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/files/image")
                        .param("path", encodedPath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_GIF));
    }

    @Test
    void getImage_withBmpFile_shouldFallbackToBmpMime() throws Exception {
        File bmpFile = tempDir.resolve("test.bmp").toFile();
        Files.write(bmpFile.toPath(), new byte[10]);
        String encodedPath = URLEncoder.encode(bmpFile.getAbsolutePath(), StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/files/image")
                        .param("path", encodedPath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("image/bmp")));
    }

    @Test
    void getImage_withWebpFile_shouldFallbackToWebpMime() throws Exception {
        File webpFile = tempDir.resolve("test.webp").toFile();
        Files.write(webpFile.toPath(), new byte[10]);
        String encodedPath = URLEncoder.encode(webpFile.getAbsolutePath(), StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/files/image")
                        .param("path", encodedPath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("image/webp")));
    }

    @Test
    void getImage_withUnknownExtension_shouldReturnOctetStream() throws Exception {
        File unknownFile = tempDir.resolve("test.xyz").toFile();
        Files.write(unknownFile.toPath(), new byte[10]);
        String encodedPath = URLEncoder.encode(unknownFile.getAbsolutePath(), StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/files/image")
                        .param("path", encodedPath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM));
    }
}