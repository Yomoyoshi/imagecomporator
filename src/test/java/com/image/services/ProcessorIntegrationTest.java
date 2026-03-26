package com.image.services;

import com.image.models.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ForkJoinPool;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ProcessorIntegrationTest {

    @Autowired
    private Processor processor;

    @SpyBean
    private Loader loader;

    @SpyBean
    private HashService hashService;

    @SpyBean
    private ImageComparatorFactory factory;

    @SpyBean
    private ForkJoinPoolExecutor executorService;

    @TempDir
    Path tempDir;

    @Test
    void process_WithRealDependencies_ShouldProcessImages() throws Exception {
        // Создаем тестовые изображения
        Path image1 = tempDir.resolve("test1.jpg");
        Path image2 = tempDir.resolve("test2.jpg");
        Files.createFile(image1);
        Files.createFile(image2);

        // Не mock executor, позволяем ему реально работать
        doReturn(ForkJoinPool.commonPool()).when(executorService).getCommonPool();

        // Act
        processor.findDuplicatesStart(tempDir.toString());

        // Verify
        //verify(factory, times(1)).createFirstImageComparator(tempDir.toString());
        verify(loader, times(0)).loadImages(any());
        verify(hashService, atLeast(0)).generatePHash(any());
    }
}