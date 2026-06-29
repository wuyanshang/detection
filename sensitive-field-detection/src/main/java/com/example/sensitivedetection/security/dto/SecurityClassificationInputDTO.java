package com.example.sensitivedetection.security.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 安全分类分级请求（对应设计文档 3.1）。
 */
@Data
public class SecurityClassificationInputDTO {

    private String systemName;
    private String systemDesc;
    private String tableName;
    private String tableChnName;
    private String columnName;

    @NotBlank(message = "columnChnName 不能为空")
    private String columnChnName;

    /** 强制重算：跳过缓存免检，重新走完整流程并覆盖原结果 */
    private boolean forceRefresh = false;
}
