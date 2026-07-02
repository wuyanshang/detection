package com.example.sensitivedetection.classify2.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 字段判定返回。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldJudgeResponse {

    /** 选定 category，未匹配为 null */
    private String category;

    @JsonProperty("matched_catalog")
    private String matchedCatalog;

    /** 命中主体（客户/投保人/员工…） */
    private String subject;

    @JsonProperty("security_level")
    private Integer securityLevel;

    private String reason;
}
