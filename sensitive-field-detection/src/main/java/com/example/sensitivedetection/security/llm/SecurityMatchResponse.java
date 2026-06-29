package com.example.sensitivedetection.security.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * AI 判定返回（对应设计文档 7.2 输出格式）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecurityMatchResponse {

    /** 选定的 category，未匹配为 null */
    private String category;

    /** 命中的关键项目录 asset 名称，未匹配为 null */
    @JsonProperty("matched_catalog")
    private String matchedCatalog;

    private String reason;
}
