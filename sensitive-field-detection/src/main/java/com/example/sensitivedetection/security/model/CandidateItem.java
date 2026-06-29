package com.example.sensitivedetection.security.model;

import lombok.Data;

/**
 * 合并去重后的候选项（对应设计文档 5.3.3），按 category 唯一。
 */
@Data
public class CandidateItem {

    private String category;
    private String asset;
    private double vectorScore;
    private double bm25Score;
    /** "vector" / "bm25" / "both" */
    private String source;
}
