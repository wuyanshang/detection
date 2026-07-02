package com.example.sensitivedetection.classify2.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 表级推断返回：整表主体 + 每字段的规范化含义/主体。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TableInferResponse {

    private String tableSubject;
    private List<FieldItem> fields = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FieldItem {
        private String columnName;
        private String normalizedName;
        private String subject;
        private String attribute;
        private String confidence;
    }
}
