package com.image.services;

import com.image.models.Duplicator;
import com.image.models.ImageComparator;
import com.image.models.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessorTest {

    @Mock
    private ForkJoinPoolExecutor executorService;

    @Mock
    private ImageComparatorFactory factory;

    @Mock
    private ImageComparator imageComparator;

    @Mock
    private ForkJoinPool forkJoinPool;

    @Mock
    private Loader loader;

    @Mock
    private Duplicator duplicator;

    @InjectMocks
    private Processor processor;

    @Test
    void findDuplicates_Start_ShouldCreateComparatorAndInvokeTask() {
        // Arrange
        String testPath = "test/path";

        File mockFile1 = mock(File.class);
        File mockFile2 = mock(File.class);
        List<File> mockImages = Arrays.asList(mockFile1, mockFile2);

        when(factory.createNextImageComparator(0, 2)).thenReturn(imageComparator);
        when(executorService.getCommonPool()).thenReturn(forkJoinPool);
        when(loader.getImages()).thenReturn(mockImages);

        doNothing().when(loader).resetCounter();
        doNothing().when(loader).loadImages(any(File.class));
        doNothing().when(duplicator).reset();

        // Act - используем join() чтобы дождаться завершения
        CompletableFuture<String> result = processor.findDuplicatesStart(testPath);
        result.join(); // Ждем завершения, но в этом тесте мы не ожидаем исключения

        // Assert
        verify(factory, times(1)).createNextImageComparator(0, 2);
        verify(executorService, times(1)).getCommonPool();
        verify(forkJoinPool, times(1)).invoke(imageComparator);
        verify(loader, times(1)).resetCounter();
        verify(duplicator, times(1)).reset();
    }

    @Test
    void findDuplicates_Start_WhenExceptionThrown_ShouldPropagateException() {
        // Arrange
        String testPath = "test/path";

        when(factory.createNextImageComparator(anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Test exception"));

        doNothing().when(loader).resetCounter();
        doNothing().when(loader).loadImages(any(File.class));
        doNothing().when(duplicator).reset();

        // Нужно, чтобы loader вернул хотя бы одно изображение
        List<File> mockImages = Collections.singletonList(mock(File.class));
        when(loader.getImages()).thenReturn(mockImages);

        // Act & Assert - используем join() чтобы поймать исключение
        CompletableFuture<String> result = processor.findDuplicatesStart(testPath);

        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                result::join // join() выбросит CompletionException, который содержит исходное исключение
        );

        verify(executorService, never()).getCommonPool();
        verify(forkJoinPool, never()).invoke(any());
        verify(loader, times(1)).resetCounter();
        verify(duplicator, times(1)).reset();
    }

    @Test
    void findDuplicates_Start_WhenInvokeThrowsException_ShouldPropagateException() {
        // Arrange
        String testPath = "test/path";

        File mockFile1 = mock(File.class);
        File mockFile2 = mock(File.class);
        List<File> mockImages = Arrays.asList(mockFile1, mockFile2);

        when(factory.createNextImageComparator(0, 2)).thenReturn(imageComparator);
        when(executorService.getCommonPool()).thenReturn(forkJoinPool);
        when(loader.getImages()).thenReturn(mockImages);

        // Mock исключение при invoke
        doThrow(new RuntimeException("Invoke failed")).when(forkJoinPool).invoke(imageComparator);

        doNothing().when(loader).resetCounter();
        doNothing().when(loader).loadImages(any(File.class));
        doNothing().when(duplicator).reset();

        // Act & Assert - используем join() чтобы поймать исключение
        CompletableFuture<String> result = processor.findDuplicatesStart(testPath);

        // join() выбросит CompletionException, обернутое в RuntimeException
        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                result::join
        );

        verify(factory, times(1)).createNextImageComparator(0, 2);
        verify(forkJoinPool, times(1)).invoke(imageComparator);
        verify(loader, times(1)).resetCounter();
        verify(duplicator, times(1)).reset();
    }
}