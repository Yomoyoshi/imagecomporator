package com.image.services;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ForkJoinPool;

@SpringBootTest
public class ForkJoinPoolExecutorTest {

    @Autowired
    private ForkJoinPoolExecutor forkJoinPoolExecutor;

    /**
     * Тест проверки метода getCommonPool()
     */
    @Test
    void testGetCommonPool() {
        ForkJoinPool commonPool = forkJoinPoolExecutor.getCommonPool();

        assertThat(commonPool).isNotNull();

        assertThat(commonPool).isEqualTo(ForkJoinPool.commonPool());
    }
}
