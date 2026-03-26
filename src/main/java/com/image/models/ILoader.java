package com.image.models;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public interface ILoader {

    void loadImages(File dir);
    void incrementConsideredImagesCounter(int start, int end);
    void resetCounter();
    void resetImages();
    List<File> getImages();
    AtomicInteger getConsideredImagesCount();
}
