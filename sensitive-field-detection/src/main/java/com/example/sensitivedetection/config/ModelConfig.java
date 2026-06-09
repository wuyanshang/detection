package com.example.sensitivedetection.config;

import lombok.Data;

@Data
public class ModelConfig {

    private String name;
    private String apiKey;
    private String baseUrl;
    private String model;
}
