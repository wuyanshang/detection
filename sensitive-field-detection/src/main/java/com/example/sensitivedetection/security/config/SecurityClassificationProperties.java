package com.example.sensitivedetection.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全分类分级配置（对应设计文档 9.1）。
 * 默认值与文档一致；DB（结果缓存 + 同义词）默认关闭，未配置数据库也能启动。
 */
@Data
@Component
@ConfigurationProperties(prefix = "security-classification")
public class SecurityClassificationProperties {

    private VectorSearch vectorSearch = new VectorSearch();
    private Bm25Search bm25Search = new Bm25Search();
    private Cache cache = new Cache();
    private Synonym synonym = new Synonym();

    private int maxRetries = 3;
    /** 批量时 LLM 最大并发 */
    private int llmConcurrencyLimit = 5;
    /** 单批最大条数 */
    private int batchSizeLimit = 500;
    private int latchTimeoutMinutes = 5;

    @Data
    public static class VectorSearch {
        private int topK = 10;
        /** HNSW 候选数 */
        private int numCandidates = 100;
        /** ES 归一化后的相似度阈值 */
        private double minScore = 0.65;
    }

    @Data
    public static class Bm25Search {
        private int topK = 10;
    }

    @Data
    public static class Cache {
        /** 是否缓存未匹配(null)结果用于免检 */
        private boolean cacheNullResult = false;
        /** 当前知识库版本，更新后递增使旧缓存失效 */
        private String ruleVersion = "v1";
        /** 缓存有效期（小时），<=0 表示永久有效 */
        private int ttlHours = 0;
    }

    @Data
    public static class Synonym {
        /** replace 替换 / expand 扩展查询 */
        private String mode = "replace";
    }
}
