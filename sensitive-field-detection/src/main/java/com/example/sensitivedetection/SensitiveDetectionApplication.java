package com.example.sensitivedetection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
        org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class
})
public class SensitiveDetectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SensitiveDetectionApplication.class, args);
    }
}
