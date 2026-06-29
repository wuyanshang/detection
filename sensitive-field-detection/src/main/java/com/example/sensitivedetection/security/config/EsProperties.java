package com.example.sensitivedetection.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 连接配置（对应设计文档 5.1）。
 * 敏感信息通过环境变量注入，不在代码中硬编码。
 */
@Data
@Component
@ConfigurationProperties(prefix = "elasticsearch")
public class EsProperties {

    /** 例 http://127.0.0.1:9200 */
    private String host = "http://127.0.0.1:9200";
    private String username;
    private String password;
    private String index = "safe_all_topic";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 10000;
}
