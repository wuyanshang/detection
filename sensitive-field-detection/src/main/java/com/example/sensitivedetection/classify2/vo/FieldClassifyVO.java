package com.example.sensitivedetection.classify2.vo;

import com.example.sensitivedetection.classify2.model.CandidateV2;
import com.example.sensitivedetection.classify2.model.FieldContext;
import lombok.Data;

import java.util.List;

/**
 * 单字段分类响应。
 */
@Data
public class FieldClassifyVO {

    private String columnName;
    private String columnChnName;

    /** 最终数据分类（完整路径）；null 表示未匹配 */
    private String category;
    /** 命中叶子名 asset */
    private String matchedCatalog;
    /** 安全级别（随命中目录带出） */
    private Integer securityLevel;
    /** 监管级别（随命中目录带出） */
    private String regulatoryLevel;
    /** 匹配类型：EXACT / FALLBACK（待复核）/ UNMATCHED */
    private String matchType;
    /** 判定主体 */
    private String subject;
    private String reason;
    private String source;

    /** 调试用 */
    private String normalizedName;
    private List<CandidateV2> candidates;

    public static FieldClassifyVO from(FieldContext ctx, boolean debug) {
        FieldClassifyVO vo = new FieldClassifyVO();
        vo.columnName = ctx.getColumnName();
        vo.columnChnName = ctx.getColumnChnName();
        vo.category = ctx.getCategory();
        vo.matchedCatalog = ctx.getMatchedCatalog();
        vo.securityLevel = ctx.getSecurityLevel();
        vo.regulatoryLevel = ctx.getRegulatoryLevel();
        vo.matchType = ctx.getMatchType();
        vo.subject = ctx.getSubject();
        vo.reason = ctx.getReason();
        vo.source = ctx.getSource();
        if (debug) {
            vo.normalizedName = ctx.getNormalizedName();
            vo.candidates = ctx.getCandidates();
        }
        return vo;
    }
}
