package com.image.services;

import org.springframework.stereotype.Service;

import java.util.concurrent.ForkJoinPool;

@Service
public class ForkJoinPoolExecutor {
    public ForkJoinPool getCommonPool() {
        return ForkJoinPool.commonPool();
    }
}
