package com.image.models;

import com.image.config.BaseConfig;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class Loader implements ILoader {
    private static final Logger log = LoggerFactory.getLogger(Loader.class);

    private List<File> images;
    @Setter
    @Getter
    private AtomicInteger consideredImagesCount = new AtomicInteger(0);
    private final BaseConfig config;
    private List<String> cachedAllowedExtensions;

    @Autowired
    public Loader(BaseConfig config) {
        this.config = config;
    }

    public void loadImages(File dir) {
        resetImages();

        Queue<File> queue = new LinkedList<>();
        queue.add(dir);

        while (!queue.isEmpty()) {
            File currentFile = queue.poll();
            if (currentFile.isDirectory()) {
                File[] files = currentFile.listFiles();
                if (Objects.nonNull(files)) {
                    queue.addAll(Arrays.asList(files));
                }
            } else if (isSupportedImageFormat(currentFile)) {
                getImages().add(currentFile);
                log.info("Added: {}", currentFile);
                if (getImages().size() == config.getSizeOfFork()) {
                    break;
                }
            }
        }
    }

    public void incrementConsideredImagesCounter(int start, int end) {
        int increment = end - start;
        int newValue = consideredImagesCount.addAndGet(increment);
        log.debug("Incremented considered images by {} (range [{}, {}), new total: {}",
                increment, start, end, newValue);
    }

    public void resetCounter() {
        consideredImagesCount.set(0);
        log.debug("Counter reset to 0");
    }

    public void resetImages() {
        images = new ArrayList<>();
    }

    public List<File> getImages() {
        if (Objects.isNull(images)) {
            resetImages();
        }
        return images;
    }

    private boolean isSupportedImageFormat(File file) {
        String fileName = file.getName().toLowerCase();
        return getAllowedExtensions().stream().anyMatch(fileName::endsWith);
    }

    private List<String> getAllowedExtensions() {
            if (Objects.isNull(cachedAllowedExtensions)) {
                cachedAllowedExtensions = Arrays.stream(config.getImageExtensions().split(","))
                        .map(String::trim)
                        .map(extension -> extension.startsWith(".") ? extension : "." + extension)
                        .map(String::toLowerCase)
                        .filter(extension -> !extension.equals("."))
                        .toList();
                log.info("Allowed extensions are: {}", cachedAllowedExtensions);
            }
            return cachedAllowedExtensions;
    }
}
