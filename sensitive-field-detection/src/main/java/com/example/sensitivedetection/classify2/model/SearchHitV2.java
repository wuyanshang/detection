package com.example.sensitivedetection.classify2.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条 ES 召回结果（v2 索引，携带 content/定义/安全级别）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchHitV2 {

    private String category;         // 完整路径
    private String asset;            // 四级叶子名
    private String content;          // 枚举示例原文
    private String level3Definition;
    private Integer securityLevel;
    private double score;
    /** "vector" 或 "bm25" */
    private String source;
}
