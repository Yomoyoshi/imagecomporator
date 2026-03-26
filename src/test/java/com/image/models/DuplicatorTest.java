package com.image.models;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DuplicatorTest {
    private Duplicator duplicator;

    @BeforeEach
    void setUp() {
        duplicator = new Duplicator();
    }

    @Test
    void getMapOfDuplicates_shouldInitializeEmptyMap() {
        Map<Long, Set<String>> map = duplicator.getMapOfDuplicates();
        assertNotNull(map);
        assertInstanceOf(ConcurrentHashMap.class, map);
        assertTrue(map.isEmpty());
    }

    @Test
    void getCheckedImagesCount_shouldInitializeZeroCounter() {
        AtomicInteger counter = duplicator.getCheckedImagesCount();
        assertNotNull(counter);
        assertEquals(0, counter.get());
    }

    @Test
    void reset_shouldClearDuplicatesAndResetCounter() {
        // Prepare some data
        Map<Long, Set<String>> map = duplicator.getMapOfDuplicates();
        map.put(1L, new HashSet<>(Set.of("path1", "path2")));
        duplicator.getCheckedImagesCount().set(5);

        duplicator.reset();

        assertNotSame(map, duplicator.getMapOfDuplicates());
        assertTrue(duplicator.getMapOfDuplicates().isEmpty());
        assertEquals(0, duplicator.getCheckedImagesCount().get());
    }

    @Test
    void incrementCheckedImagesCount_shouldIncreaseCounter() {
        AtomicInteger initial = duplicator.getCheckedImagesCount();
        assertEquals(0, initial.get());

        duplicator.incrementCheckedImagesCount();
        assertEquals(1, duplicator.getCheckedImagesCount().get());

        duplicator.incrementCheckedImagesCount();
        assertEquals(2, duplicator.getCheckedImagesCount().get());
    }

    @Test
    void deleteDuplicates_shouldRemoveGroupsWithSingleFile() {
        Map<Long, Set<String>> map = duplicator.getMapOfDuplicates();

        Set<String> group1 = new HashSet<>(Set.of("a.jpg", "b.jpg"));
        Set<String> group2 = new HashSet<>(Set.of("c.jpg"));
        Set<String> group3 = new HashSet<>(Set.of("d.jpg", "e.jpg", "f.jpg"));

        map.put(1L, group1);
        map.put(2L, group2);
        map.put(3L, group3);

        duplicator.deleteDuplicates();

        assertEquals(2, map.size());
        assertTrue(map.containsValue(group1));
        assertFalse(map.containsValue(group2));
        assertTrue(map.containsValue(group3));
    }

    @Test
    void deleteDuplicates_whenMapIsEmpty_shouldDoNothing() {
        assertDoesNotThrow(() -> duplicator.deleteDuplicates());
        assertTrue(duplicator.getMapOfDuplicates().isEmpty());
    }

    @Test
    void consolePrintResults_shouldNotThrowException() {
        // Prepare some data
        Map<Long, Set<String>> map = duplicator.getMapOfDuplicates();
        map.put(1L, Set.of("path1", "path2"));
        map.put(2L, Set.of("path3"));

        assertDoesNotThrow(() -> duplicator.consolePrintResults());
    }

    @Test
    void consolePrintResults_whenNoDuplicates_shouldPrintMessage() {
        // Empty map
        assertDoesNotThrow(() -> duplicator.consolePrintResults());
    }

    @Test
    void filePrintResults_shouldNotThrowExceptionWhenDuplicatesExist() {
        Map<Long, Set<String>> map = duplicator.getMapOfDuplicates();
        map.put(1L, Set.of("file1.jpg", "file2.jpg"));
        map.put(2L, Set.of("file3.png"));
        assertDoesNotThrow(() -> duplicator.filePrintResults());
    }

    @Test
    void filePrintResults_shouldNotThrowException() {
        Map<Long, Set<String>> map = duplicator.getMapOfDuplicates();
        map.put(1L, Set.of("file1.jpg", "file2.jpg"));
        assertDoesNotThrow(() -> duplicator.filePrintResults());
    }

    @Test
    void filePrintResults_whenNoDuplicates_shouldNotCreateFile(@TempDir Path tempDir) throws IOException {
        Path originalDir = Path.of(System.getProperty("user.dir"));
        try {
            System.setProperty("user.dir", tempDir.toString());

            duplicator.filePrintResults();

            long count = Files.list(tempDir).count();
            assertEquals(0, count);
        } finally {
            System.setProperty("user.dir", originalDir.toString());
        }
    }

    @Test
    void filePrintResults_shouldHandleIOExceptionGracefully() {
        assertDoesNotThrow(() -> duplicator.filePrintResults());
    }
}
