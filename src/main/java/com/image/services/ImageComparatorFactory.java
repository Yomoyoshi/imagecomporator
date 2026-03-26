package com.image.services;

import com.image.config.BaseConfig;
import com.image.models.Duplicator;
import com.image.models.ImageComparator;
import com.image.models.Loader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class ImageComparatorFactory {
    private static final Logger log = LoggerFactory.getLogger(ImageComparatorFactory.class);

    Loader loader;
    HashService hash;
    Duplicator duplicator;
    BaseConfig config;
    private final ApplicationContext context;

    @Autowired
    public ImageComparatorFactory(ApplicationContext context, BaseConfig config, Loader loader, Duplicator duplicator, HashService hash) {
        this.duplicator = duplicator;
        this.hash = hash;
        this.loader = loader;
        this.config = config;
        this.context = context;
    }

    public ImageComparator createFirstImageComparator(String pathname) {
        duplicator.reset();
        loader.resetCounter();
        loader.loadImages(new File(pathname));
        log.info("Найдено {} файлов", loader.getImages().size());
        return createNextImageComparator(0, loader.getImages().size());
    }

    public ImageComparator createNextImageComparator(int start, int end) {
        // Создаем новый экземпляр ImageComparator через Spring context
        ImageComparator comparator = context.getBean(ImageComparator.class,
                config, duplicator, loader, hash, this);
        comparator.setStart(start);
        comparator.setEnd(end);
        return comparator;
    }
}
