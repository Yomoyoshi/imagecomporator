package com.image.models;

import com.image.config.BaseConfig;
import com.image.services.ImageComparatorFactory;
import com.image.services.HashService;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.RecursiveTask;

@Component
@Scope("prototype")
public class ImageComparator extends RecursiveTask<Void> {

    private static final Logger log = LoggerFactory.getLogger(ImageComparator.class);

    @Getter
    @Setter
    private int start;
    @Getter
    @Setter
    private int end;

    private final Loader loader;
    private final HashService hashService;
    private final ImageComparatorFactory factory;
    private final Duplicator duplicator;
    private final BaseConfig config;

    @Autowired
    public ImageComparator(BaseConfig config, Duplicator duplicator,
                           Loader loader, HashService hashService,
                           ImageComparatorFactory factory) {
        this.loader = loader;
        this.hashService = hashService;
        this.factory = factory;
        this.duplicator = duplicator;
        this.config = config;
    }

    @Override
    public Void compute() {
        int size = end - start;

        if (loader.getImages().isEmpty()) {
            return null;
        }

        if (size <= Math.max(loader.getImages().size() / config.getSizeOfFork(), config.getSizeOfPage())) {
            findDuplicates();
        } else {
            int mid = size / 2;
            ImageComparator leftTask = factory.createNextImageComparator(start, start + mid);
            ImageComparator rightTask = factory.createNextImageComparator(start + mid, end);
            invokeAll(leftTask, rightTask);
        }
        return null;
    }

    public void findDuplicates() {
        log.info("Searching duplicates in range [{}, {}]", start, end);

        List<File> images = loader.getImages();
        int processedInThisTask = 0;

        try {
            for (int i = start; i < end && i < images.size(); i++) {
                File image = images.get(i);
                try {
                    long phash = hashService.generatePHash(image);

                    duplicator.getMapOfDuplicates()
                            .computeIfAbsent(phash, k -> new HashSet<>())
                            .add(image.getAbsolutePath());

                    processedInThisTask++;

                } catch (IOException e) {
                    log.error("Error reading file: {}", image.getAbsolutePath(), e);
                } catch (Exception e) {
                    log.error("Error processing file: {}", image.getAbsolutePath(), e);
                }

                // Увеличиваем счетчик ПОСЛЕ каждого файла
                duplicator.incrementCheckedImagesCount();
            }
        } catch (Exception e) {
            log.error("Error in findDuplicates for range [{}, {}]", start, end, e);
        }

        // Увеличиваем счетчик рассмотренных изображений в Loader
        if (processedInThisTask > 0) {
            loader.incrementConsideredImagesCounter(start, end);
            log.info("Completed range [{}, {}], processed {} files", start, end, processedInThisTask);
        } else {
            log.warn("No files processed in range [{}, {}]", start, end);
        }
    }
}