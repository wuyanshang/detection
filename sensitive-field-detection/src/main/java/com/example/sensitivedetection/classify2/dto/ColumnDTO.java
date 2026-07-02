package com.example.sensitivedetection.classify2.dto;

import lombok.Data;

/**
 * 单个字段（表级请求里的一项）。
 */
@Data
public class ColumnDTO {

    private String columnName;      // 英文名
    private String columnChnName;   // 中文名
}
