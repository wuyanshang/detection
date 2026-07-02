package com.example.sensitivedetection.classify2.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 表级分类请求：一次一整张表 + 其字段清单。
 * 整表信息用于"表级含义推断"（锁定主体，解决 客户姓名/投保人姓名 误配）。
 */
@Data
public class TableClassifyRequest {

    private String systemName;
    private String systemDesc;

    @NotBlank(message = "tableName 不能为空")
    private String tableName;
    private String tableChnName;

    /** 该表的字段清单 */
    private List<ColumnDTO> columns = new ArrayList<>();
}
