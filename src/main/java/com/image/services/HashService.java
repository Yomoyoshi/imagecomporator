package com.image.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@Service
public class HashService {
    private static final Logger log = LoggerFactory.getLogger(HashService.class);

    private static final int HASH_SIZE = 8;

    public long generatePHash(File imageFile) throws IOException {

        if (imageFile == null) {
            throw new IllegalArgumentException("Image file cannot be null");
        }

        BufferedImage img = ImageIO.read(imageFile);

        if (img == null) {
            throw new IOException("Cannot read image file: " + imageFile.getAbsolutePath());
        }

        img = resize(img);
        if (log.isDebugEnabled()) {
            log.debug("height: {}, width: {}", img.getHeight(), img.getWidth());
        }
        double[][] dctMatrix = discreteCosineTransform(convertToGrayscale(img)); // Преобразование Фурье
        double medianValue = calculateMedian(dctMatrix);
        return convertDCTToBinaryHash(dctMatrix, medianValue);
    }

    private double[][] discreteCosineTransform(double[][] matrix) {
        int n = matrix.length;
        double[][] output = new double[n][n];

        for (int u = 0; u < n; u++) {
            for (int v = 0; v < n; v++) {
                double sum = 0.0;
                for (int x = 0; x < n; x++) {
                    for (int y = 0; y < n; y++) {
                        sum += matrix[x][y] *
                                Math.cos(Math.PI * (2 * x + 1) * u / (2 * n)) *
                                Math.cos(Math.PI * (2 * y + 1) * v / (2 * n));
                    }
                }
                output[u][v] = sum * C(u) * C(v) / n;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Матрица DCT: {}", Arrays.deepToString(output));
        }
        return output;
    }

    private double C(int i) {
        return i == 0 ? 1 / Math.sqrt(2) : 1.0;
    }

    private double calculateMedian(double[][] matrix) {
        int size = matrix.length * matrix.length;
        double[] values = new double[size];
        int index = 0;
        for (double[] row : matrix) {
            for (double value : row) {
                values[index++] = value;
            }
        }
        Arrays.sort(values);
        double medianValue = values[(size - 1)/2];
        if (log.isDebugEnabled()) {
            log.debug("Медианное значение: {}", medianValue);
        }
        return medianValue; // Берём среднее значение
    }

    private static double[][] convertToGrayscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        double[][] grayScale = new double[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                grayScale[x][y] = 0.299*r + 0.587*g + 0.114*b; // Формула преобразования RGB в серый оттенок
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Матрица оттенков серого: {}", Arrays.deepToString(grayScale));
        }
        return grayScale;
    }

    private long convertDCTToBinaryHash(double[][] matrix, double threshold) {
        long hash = 0L;
        for (int i = 0; i < matrix.length; ++i) {
            for (int j = 0; j < matrix[i].length; ++j) {
                if (matrix[i][j] >= threshold) {
                    hash |= (1L << (i*HASH_SIZE+j));
                }
            }
        }
        return hash;
    }

    private BufferedImage resize(BufferedImage originalImage) {
        BufferedImage resizedImage = new BufferedImage(HASH_SIZE, HASH_SIZE, BufferedImage.TYPE_INT_RGB);
        resizedImage.createGraphics().drawImage(originalImage, 0, 0, HASH_SIZE, HASH_SIZE, null);
        return resizedImage;
    }
}
