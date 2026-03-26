package com.image.services;

import com.image.models.Duplicator;
import com.image.models.ILoader;
import com.image.models.ImageComparator;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class Processor implements ProcessorService {
    private static final Logger log = LoggerFactory.getLogger(Processor.class);

    private final ForkJoinPoolExecutor executorService;
    private final ImageComparatorFactory factory;
    @Getter
    private final ILoader loader;
    private final Duplicator duplicator;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private String currentFolder = null;

    @Autowired
    public Processor(Duplicator duplicator,
                     ILoader loader,
                     ForkJoinPoolExecutor executorService,
                     ImageComparatorFactory factory
    ) {
        this.executorService = executorService;
        this.factory = factory;
        this.loader = loader;
        this.duplicator = duplicator;
    }

    @Override
    @Async
    public CompletableFuture<String> findDuplicatesStart(String folderPath) {
        if (!isProcessing.compareAndSet(false, true)) {
            String error = "Already processing folder: " + currentFolder;
            log.warn(error);
            return CompletableFuture.failedFuture(new IllegalStateException(error));
        }

        String jobId = java.util.UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        currentFolder = folderPath;

        try {
            log.info("Starting processing folder: {} with jobId: {}", folderPath, jobId);

            getLoader().resetCounter();
            getDuplicator().reset();

            getLoader().loadImages(new java.io.File(folderPath));

            int totalImages = getLoader().getImages().size();
            log.info("Found {} images to process", totalImages);

            if (totalImages == 0) {
                log.warn("No images found in folder: {}", folderPath);
                isProcessing.set(false);
                return CompletableFuture.completedFuture(jobId);
            }

            ImageComparator task = factory.createNextImageComparator(0, totalImages);
            executorService.getCommonPool().invoke(task);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Completed processing folder: {} in {} ms", folderPath, duration);

        } catch (Exception e) {
            log.error("Error processing folder: {}", folderPath, e);
            isProcessing.set(false);
            return CompletableFuture.failedFuture(e);
        }

        isProcessing.set(false);
        currentFolder = null;
        return CompletableFuture.completedFuture(jobId);
    }

    @Override
    public float findDuplicatesProgress() {
        if (getLoader().getImages() == null || getLoader().getImages().isEmpty()) {
            return 0;
        }

        int considered = getLoader().getConsideredImagesCount().get();
        int total = getLoader().getImages().size();

        if (total == 0) {
            return 0;
        }

        float progress = (float) (100 * considered) / total;
        return Math.round(progress * 10) / 10.0f;
    }

    @Override
    public List<List<String>> findDuplicatesResult() {
        if (isProcessing()) {
            log.warn("Attempting to get results while processing is still active");
            return new ArrayList<>();
        }

        getDuplicator().deleteDuplicates();
        getDuplicator().consolePrintResults();

        Map<Long, Set<String>> duplicates = getDuplicator().getMapOfDuplicates();
        List<List<String>> result = new ArrayList<>();

        for (Set<String> duplicateGroup : duplicates.values()) {
            if (duplicateGroup.size() > 1) {
                result.add(new ArrayList<>(duplicateGroup));
            }
        }

        log.info("Found {} duplicate groups", result.size());
        return result;
    }

    @Override
    public boolean isProcessing() {
        return isProcessing.get();
    }

    @Override
    public String getCurrentFolder() {
        return currentFolder;
    }

    @Override
    public int getTotalImages() {
        return getLoader().getImages() != null ? getLoader().getImages().size() : 0;
    }

    @Override
    public int getProcessedImages() {
        return getLoader().getConsideredImagesCount() != null ? getLoader().getConsideredImagesCount().get() : 0;
    }

    @Override
    public Duplicator getDuplicator() {
        return duplicator;
    }
}
