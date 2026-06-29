package com.example.sensitivedetection.security.model;

import com.example.sensitivedetection.security.dto.SecurityClassificationInputDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单字段安全分类分级的全流程状态对象（对应设计文档 8.2）。
 * 在工作流节点间通过 OverAllState 的 "result" 键传递与逐步填充。
 */
@Data
public class SecurityClassificationResult {

    // ---- 输入 ----
    private String systemName;
    private String systemDesc;
    private String tableName;
    private String tableChnName;
    private String columnName;
    private String columnChnName;
    private boolean forceRefresh;

    // ---- 缓存 ----
    private String cacheKey;     // systemName|tableName|columnName
    private boolean exempt;      // 命中且有效的缓存

    // ---- 中间状态 ----
    /** 同义词替换/扩展后用于检索的查询词（replace 模式为单个；expand 模式可多个） */
    private List<String> queryTerms = new ArrayList<>();
    private List<SearchResult> vectorResults = new ArrayList<>();
    private List<SearchResult> bm25Results = new ArrayList<>();
    private List<CandidateItem> candidates = new ArrayList<>();

    // ---- 输出 ----
    private String category;        // null 表示未匹配
    private String matchedCatalog;  // null 表示未匹配
    private String reason;
    private String source;          // cache / llm / no-candidate / degraded

    public static SecurityClassificationResult from(SecurityClassificationInputDTO in) {
        SecurityClassificationResult r = new SecurityClassificationResult();
        r.systemName = nz(in.getSystemName());
        r.systemDesc = nz(in.getSystemDesc());
        r.tableName = nz(in.getTableName());
        r.tableChnName = nz(in.getTableChnName());
        r.columnName = nz(in.getColumnName());
        r.columnChnName = nz(in.getColumnChnName());
        r.forceRefresh = in.isForceRefresh();
        r.cacheKey = buildCacheKey(r.systemName, r.tableName, r.columnName);
        return r;
    }

    public static String buildCacheKey(String systemName, String tableName, String columnName) {
        return nz(systemName) + "|" + nz(tableName) + "|" + nz(columnName);
    }

    /** 主检索词：取 queryTerms 第一个，缺省回退到字段中文名。 */
    public String primaryQuery() {
        if (queryTerms != null && !queryTerms.isEmpty()) {
            return queryTerms.get(0);
        }
        return columnChnName;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
