package com.example.sensitivedetection.security.vo;

import com.example.sensitivedetection.security.model.CandidateItem;
import com.example.sensitivedetection.security.model.SecurityClassificationResult;
import lombok.Data;

import java.util.List;

/**
 * 安全分类分级响应（对应设计文档 3.1）。对外驼峰命名。
 */
@Data
public class SecurityClassificationOutputVO {

    private String systemName;
    private String tableName;
    private String columnName;

    /** 最终判定的数据分类；null 表示未匹配 */
    private String category;
    /** 命中的关键项目录名称(asset)；null 表示未匹配 */
    private String matchedCatalog;
    private String reason;

    /** 调试用：合并去重后的候选列表（可选） */
    private List<CandidateItem> candidates;

    public static SecurityClassificationOutputVO from(SecurityClassificationResult r, boolean includeDebug) {
        SecurityClassificationOutputVO vo = new SecurityClassificationOutputVO();
        vo.systemName = r.getSystemName();
        vo.tableName = r.getTableName();
        vo.columnName = r.getColumnName();
        vo.category = r.getCategory();
        vo.matchedCatalog = r.getMatchedCatalog();
        vo.reason = r.getReason();
        if (includeDebug) {
            vo.candidates = r.getCandidates();
        }
        return vo;
    }
}
