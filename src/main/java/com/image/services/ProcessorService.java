package com.image.services;

import com.image.models.Duplicator;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ProcessorService {
    CompletableFuture<String> findDuplicatesStart(String folderPath);
    float findDuplicatesProgress();
    List<List<String>> findDuplicatesResult();
    boolean isProcessing();
    String getCurrentFolder();
    int getTotalImages();
    int getProcessedImages();
    Duplicator getDuplicator(); // Если нужен доступ к Duplicator
}
