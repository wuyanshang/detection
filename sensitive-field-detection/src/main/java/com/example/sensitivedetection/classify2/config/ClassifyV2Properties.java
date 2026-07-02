package com.example.sensitivedetection.classify2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * classify-v2 独立配置（新增，不影响现有 security-classification）。
 * 指向新索引 safe_all_topic_v2，召回参数比旧版更"慷慨"（300 条目录无压力）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "classify-v2")
public class ClassifyV2Properties {

    /** 新索引名（含 content/example/定义 字段） */
    private String index = "safe_all_topic_v2";

    private VectorSearch vectorSearch = new VectorSearch();
    private Bm25Search bm25Search = new Bm25Search();

    /** 表级含义推断开关；关闭时退化为仅用原始字段中文名检索 */
    private boolean tableInferEnabled = true;
    /** 判定时启用"主体一致性"约束（客户/投保人/员工 不串） */
    private boolean subjectConsistency = true;

    private int maxRetries = 3;
    /** 逐字段处理时的最大并发 */
    private int concurrencyLimit = 5;
    /** 单表字段数上限 */
    private int columnsLimit = 500;
    private int latchTimeoutMinutes = 5;

    @Data
    public static class VectorSearch {
        private int topK = 25;
        private int numCandidates = 300;
        private double minScore = 0.5;
    }

    @Data
    public static class Bm25Search {
        private int topK = 25;
    }
}
