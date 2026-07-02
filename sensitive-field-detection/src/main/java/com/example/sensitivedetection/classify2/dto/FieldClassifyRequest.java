package com.example.sensitivedetection.classify2.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 单字段分类请求（兜底接口）。拿不到同表上下文时使用，主体推断会降级。
 */
@Data
public class FieldClassifyRequest {

    private String systemName;
    private String systemDesc;
    private String tableName;
    private String tableChnName;
    private String columnName;

    @NotBlank(message = "columnChnName 不能为空")
    private String columnChnName;
}
