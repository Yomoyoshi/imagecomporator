package com.image.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app")
@Validated
public class BaseConfig {

    @Positive(message = "Size of fork must be positive")
    @Min(value = 1, message = "Size of fork must be at least 1")
    private int sizeOfFork;

    @Positive(message = "Size of page must be positive")
    @Min(value = 1, message = "Size of page must be at least 1")
    private int sizeOfPage;

    @NotBlank(message = "Image extensions cannot be empty")
    private String imageExtensions = "jpg,jpeg,png,bmp";

    @Positive(message = "Max image size must be positive")
    @Max(value = 100 * 1024 * 1024, message = "Max image size cannot exceed 100MB")
    private long maxImageSize = 10 * 1024 * 1024; // 10MB по умолчанию

    private boolean cacheEnabled = true;
    private int cacheSize = 100;
}
