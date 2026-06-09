package com.example.sensitivedetection.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sensitive-detection")
public class DetectionProperties {

    private int maxRetries = 3;
    private String outputFile = "output/detection_result.xlsx";
}
