package com.example.sensitivedetection.security.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条 ES 召回结果（向量或 BM25）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private String category;
    private String asset;
    private double score;
    /** "vector" 或 "bm25" */
    private String source;
}
