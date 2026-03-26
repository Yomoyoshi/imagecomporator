package com.image.controllers;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    @GetMapping("/image")
    public ResponseEntity<Resource> getImage(@RequestParam String path) {
        try {
            String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
            log.info("Requesting image: {}", decodedPath);

            File file = new File(decodedPath);

            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }

            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType == null) {
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                    mimeType = "image/jpeg";
                } else if (fileName.endsWith(".png")) {
                    mimeType = "image/png";
                } else if (fileName.endsWith(".gif")) {
                    mimeType = "image/gif";
                } else if (fileName.endsWith(".bmp")) {
                    mimeType = "image/bmp";
                } else if (fileName.endsWith(".webp")) {
                    mimeType = "image/webp";
                } else {
                    mimeType = "application/octet-stream";
                }
            }

            Resource resource = new FileSystemResource(file);

            // Отправляем без Content-Disposition
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mimeType))
                    .contentLength(file.length())
                    .body(resource);

        } catch (Exception e) {
            log.error("Error serving image", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/info")
    public ResponseEntity<FileInfo> getFileInfo(@RequestParam String path) {
        try {
            String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
            log.info("Getting file info: {}", decodedPath);

            File file = new File(decodedPath);

            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }

            FileInfo info = new FileInfo();
            info.setPath(decodedPath);
            info.setFilename(file.getName());
            info.setSize(file.length());
            info.setLastModified(file.lastModified());

            return ResponseEntity.ok(info);

        } catch (Exception e) {
            log.error("Error getting file info: {}", path, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, String>> deleteFile(@RequestBody Map<String, String> request) {
        try {
            String path = request.get("path");
            String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);

            log.info("Deleting file: {}", decodedPath);

            File file = new File(decodedPath);

            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            if (file.delete()) {
                log.info("File deleted successfully: {}", decodedPath);
                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "File deleted"
                ));
            } else {
                log.error("Could not delete file: {}", decodedPath);
                return ResponseEntity.internalServerError()
                        .body(Map.of(
                                "status", "error",
                                "message", "Could not delete file"
                        ));
            }

        } catch (Exception e) {
            log.error("Error deleting file", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "status", "error",
                            "message", e.getMessage()
                    ));
        }
    }

    @Setter
    @Getter
    static class FileInfo {
        private String path;
        private String filename;
        private long size;
        private long lastModified;

    }
}