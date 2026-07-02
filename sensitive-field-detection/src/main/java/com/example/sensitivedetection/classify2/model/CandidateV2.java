package com.example.sensitivedetection.classify2.model;

import lombok.Data;

/**
 * 合并去重后的候选项（按 category 唯一），携带判定所需的全部上下文。
 */
@Data
public class CandidateV2 {

    private String category;          // 完整路径（一|二|三|四）
    private String asset;             // 四级叶子名
    private String content;           // 枚举示例，判定的核心证据
    private String level3Definition;  // 三级定义，消歧上下文
    private Integer securityLevel;    // 命中即回填输出
    private double vectorScore;
    private double bm25Score;
    /** "vector" / "bm25" / "both" */
    private String source;
}
