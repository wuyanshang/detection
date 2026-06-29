package com.example.sensitivedetection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
        org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class
})
public class SensitiveDetectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SensitiveDetectionApplication.class, args);
    }
}
