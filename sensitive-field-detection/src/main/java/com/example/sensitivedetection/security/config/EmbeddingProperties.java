package com.example.sensitivedetection.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Embedding 模型配置（对应设计文档 6.1 / 9.1）。
 * 走 DashScope 的 OpenAI 兼容 embeddings 接口。
 */
@Data
@Component
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String apiKey;
    private String model = "text-embedding-v4";
    private int dimension = 1024;
    private int readTimeoutMs = 15000;

    /** 简单本地缓存（按文本），避免重复字段重复请求 */
    private Cache cache = new Cache();

    @Data
    public static class Cache {
        private boolean enabled = true;
        private int maxSize = 10000;
    }
}
