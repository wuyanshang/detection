package com.example.sensitivedetection.classify2.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单字段分类的全流程状态对象，在 graph 节点间通过 "result" 键传递。
 */
@Data
public class FieldContext {

    // ---- 输入 ----
    private String systemName;
    private String systemDesc;
    private String tableName;
    private String tableChnName;
    private String columnName;
    private String columnChnName;

    // ---- 表级推断注入 ----
    private String tableSubject;   // 整表主体，如 "投保人信息"
    private String fieldSubject;   // 本字段主体，如 "投保人"
    private String normalizedName; // 规范化检索词
    private String confidence;     // high / low

    // ---- 中间状态 ----
    private List<String> queryTerms = new ArrayList<>();
    private List<SearchHitV2> vectorResults = new ArrayList<>();
    private List<SearchHitV2> bm25Results = new ArrayList<>();
    private List<CandidateV2> candidates = new ArrayList<>();

    // ---- 输出 ----
    private String category;        // null 表示未匹配
    private String matchedCatalog;  // 命中叶子名 asset
    private Integer securityLevel;  // 随命中目录带出
    private String subject;         // 最终判定主体
    private String reason;
    private String source;          // llm / no-candidate / degraded

    /** 主检索词：queryTerms 第一个，缺省回退字段中文名。 */
    public String primaryQuery() {
        if (queryTerms != null && !queryTerms.isEmpty() && queryTerms.get(0) != null
                && !queryTerms.get(0).isBlank()) {
            return queryTerms.get(0);
        }
        return columnChnName;
    }
}
