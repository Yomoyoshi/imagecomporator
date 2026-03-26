package com.image.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class HashServiceTest {

    @InjectMocks
    private HashService hashService;

    @TempDir
    Path tempDir;

    private File validImageFile;
    private File invalidImageFile;
    private File corruptedImageFile;

    @BeforeEach
    void setUp() throws IOException {
        // Создаем валидное тестовое изображение с разнообразным содержимым
        validImageFile = tempDir.resolve("test.jpg").toFile();
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        // Заполняем градиентом, чтобы хэш не был нулевым
        for (int x = 0; x < 100; x++) {
            for (int y = 0; y < 100; y++) {
                int r = (x * 255) / 100;
                int g = (y * 255) / 100;
                int b = ((x + y) * 255) / 200;
                int rgb = (r << 16) | (g << 8) | b;
                image.setRGB(x, y, rgb);
            }
        }
        ImageIO.write(image, "png", validImageFile); // Используем PNG для сохранения качества

        // Невалидный файл (текстовый)
        invalidImageFile = tempDir.resolve("test.txt").toFile();
        Files.writeString(invalidImageFile.toPath(), "not an image");

        // Поврежденный файл (некорректные данные)
        corruptedImageFile = tempDir.resolve("corrupted.jpg").toFile();
        Files.write(corruptedImageFile.toPath(), new byte[]{0x00, 0x01, 0x02, 0x03});
    }

    @Test
    void generatePHash_WithValidImage_ShouldReturnNonZeroHash() throws IOException {
        long hash = hashService.generatePHash(validImageFile);
        // Хэш может быть любым, главное, что не 0 для изображения с градиентом
        assertNotEquals(0L, hash, "Hash should not be zero for gradient image");
    }

    @Test
    void generatePHash_WithSameImage_ShouldReturnSameHash() throws IOException {
        File image1 = validImageFile;
        // Копируем файл напрямую, а не сохраняем снова через ImageIO
        File image2 = tempDir.resolve("test_copy.png").toFile();
        Files.copy(image1.toPath(), image2.toPath(), StandardCopyOption.COPY_ATTRIBUTES);

        long hash1 = hashService.generatePHash(image1);
        long hash2 = hashService.generatePHash(image2);

        assertEquals(hash1, hash2, "Identical files should produce identical hashes");
    }

    @Test
    void generatePHash_WithDifferentImages_ShouldReturnDifferentHashes() throws IOException {
        // Градиентное изображение
        File image1 = tempDir.resolve("gradient.png").toFile();
        BufferedImage gradientImg = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 100; x++) {
            for (int y = 0; y < 100; y++) {
                int rgb = (x * 255 / 100) << 16 | (y * 255 / 100) << 8 | ((x + y) * 255 / 200);
                gradientImg.setRGB(x, y, rgb);
            }
        }
        ImageIO.write(gradientImg, "png", image1);

        // Сплошной черный цвет
        File image2 = tempDir.resolve("black.png").toFile();
        BufferedImage blackImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 100; x++) {
            for (int y = 0; y < 100; y++) {
                blackImage.setRGB(x, y, 0x000000);
            }
        }
        ImageIO.write(blackImage, "png", image2);

        long hash1 = hashService.generatePHash(image1);
        long hash2 = hashService.generatePHash(image2);

        assertNotEquals(hash1, hash2, "Different images should have different hashes");
    }

    @Test
    void generatePHash_WithInvalidImage_ShouldThrowIOException() {
        Exception exception = assertThrows(IOException.class, () -> hashService.generatePHash(invalidImageFile));
        assertTrue(exception.getMessage().contains("Cannot read image file"));
    }

    @Test
    void generatePHash_WithCorruptedImage_ShouldThrowIOException() {
        Exception exception = assertThrows(IOException.class, () -> hashService.generatePHash(corruptedImageFile));
        assertTrue(exception.getMessage().contains("Cannot read image file"));
    }

    @Test
    void generatePHash_WithNullImage_ShouldThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> hashService.generatePHash(null));
    }

    @Test
    void generatePHash_WithSmallImage_ShouldResizeCorrectly() throws IOException {
        File smallImage = tempDir.resolve("small.png").toFile();
        BufferedImage smallImg = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                smallImg.setRGB(x, y, (x * y) % 0xFFFFFF);
            }
        }
        ImageIO.write(smallImg, "png", smallImage);

        long hash = hashService.generatePHash(smallImage);
        assertNotEquals(0L, hash, "Small image should produce non-zero hash");
    }

    @Test
    void generatePHash_WithLargeImage_ShouldResizeCorrectly() throws IOException {
        File largeImage = tempDir.resolve("large.png").toFile();
        BufferedImage largeImg = new BufferedImage(2000, 2000, BufferedImage.TYPE_INT_RGB);
        // Заполняем, чтобы не было пустым
        for (int x = 0; x < 2000; x++) {
            for (int y = 0; y < 2000; y++) {
                largeImg.setRGB(x, y, (x * y) % 0xFFFFFF);
            }
        }
        ImageIO.write(largeImg, "png", largeImage);

        long hash = hashService.generatePHash(largeImage);
        assertNotEquals(0L, hash, "Large image should produce non-zero hash");
    }

    @Test
    void generatePHash_WithDifferentFormats_ShouldHandleAllFormats() throws IOException {
        // Создаем тестовое изображение
        BufferedImage testImg = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 50; x++) {
            for (int y = 0; y < 50; y++) {
                testImg.setRGB(x, y, (x * y) % 0xFFFFFF);
            }
        }

        // Test with PNG
        File pngImage = tempDir.resolve("test.png").toFile();
        ImageIO.write(testImg, "png", pngImage);

        // Test with BMP
        File bmpImage = tempDir.resolve("test.bmp").toFile();
        ImageIO.write(testImg, "bmp", bmpImage);

        // Test with GIF
        File gifImage = tempDir.resolve("test.gif").toFile();
        ImageIO.write(testImg, "gif", gifImage);

        assertDoesNotThrow(() -> hashService.generatePHash(pngImage));
        assertDoesNotThrow(() -> hashService.generatePHash(bmpImage));
        assertDoesNotThrow(() -> hashService.generatePHash(gifImage));
    }

    @Test
    void generatePHash_ShouldReturnConsistentHash_ForSameImageMultipleTimes() throws IOException {
        long hash1 = hashService.generatePHash(validImageFile);
        long hash2 = hashService.generatePHash(validImageFile);
        long hash3 = hashService.generatePHash(validImageFile);

        assertEquals(hash1, hash2, "Hash should be consistent across multiple calls");
        assertEquals(hash2, hash3, "Hash should be consistent across multiple calls");
    }

    @Test
    void generatePHash_ShouldReturnHashWithinLongRange() throws IOException {
        long hash = hashService.generatePHash(validImageFile);
        // Хэш может быть любым значением long (включая отрицательные)
        // Просто проверяем, что он не равен 0 для изображения с содержимым
        assertNotEquals(0L, hash, "Hash should not be zero");
    }

    @Test
    void generatePHash_WithGrayscaleConversion_ShouldWorkCorrectly() throws IOException {
        File colorImage = tempDir.resolve("color.png").toFile();
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 100; x++) {
            for (int y = 0; y < 100; y++) {
                img.setRGB(x, y, 0xFF0000); // Red color
            }
        }
        ImageIO.write(img, "png", colorImage);

        long hash = hashService.generatePHash(colorImage);
        assertNotEquals(0L, hash, "Color image should produce non-zero hash");
    }

    @Test
    void generatePHash_WithEdgeCaseImage_ShouldNotThrowException() throws IOException {
        File edgeImage = tempDir.resolve("edge.png").toFile();
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFFFFFF);
        ImageIO.write(img, "png", edgeImage);

        assertDoesNotThrow(() -> hashService.generatePHash(edgeImage));
        hashService.generatePHash(edgeImage);
        // Для 1x1 изображения хэш будет вычислен, он может быть 0 или нет
        // Просто проверяем, что метод не выбрасывает исключение
    }

    @Test
    void generatePHash_WithSameContent_ShouldReturnSameHash() throws IOException {
        // Создаем два изображения с одинаковым содержимым через копирование файла
        File imageA = tempDir.resolve("img_a.png").toFile();
        File imageB = tempDir.resolve("img_b.png").toFile();

        BufferedImage img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 50; x++) {
            for (int y = 0; y < 50; y++) {
                img.setRGB(x, y, (x + y) % 0xFFFFFF);
            }
        }
        ImageIO.write(img, "png", imageA);
        Files.copy(imageA.toPath(), imageB.toPath(), StandardCopyOption.COPY_ATTRIBUTES);

        long hashA = hashService.generatePHash(imageA);
        long hashB = hashService.generatePHash(imageB);

        assertEquals(hashA, hashB, "Identical images should have identical hashes");
    }

    @Test
    void generatePHash_WithSolidColorImage_MayReturnZero() throws IOException {
        // Создаем полностью черное изображение
        File blackImage = tempDir.resolve("solid_black.png").toFile();
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 100; x++) {
            for (int y = 0; y < 100; y++) {
                img.setRGB(x, y, 0x000000);
            }
        }
        ImageIO.write(img, "png", blackImage);

        // Для полностью однородного изображения хэш может быть 0
        // Это ожидаемое поведение, тест просто проверяет что нет исключения
        assertDoesNotThrow(() -> hashService.generatePHash(blackImage));
    }
}