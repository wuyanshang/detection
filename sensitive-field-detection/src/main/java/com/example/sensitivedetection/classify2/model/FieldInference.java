package com.example.sensitivedetection.classify2.model;

import lombok.Data;

/**
 * 表级推断得到的单字段结果：规范化检索词 + 主体。
 */
@Data
public class FieldInference {

    private String columnName;
    /** 规范化后的检索词，如 "姓名"→"投保人姓名"、"zjhm"→"投保人证件号码" */
    private String normalizedName;
    /** 主体，如 客户/投保人/员工/受益人；用于判定主体一致性 */
    private String subject;
    /** 属性，如 姓名/电话/证件号码 */
    private String attribute;
    /** high / low；low 时召回同时用原词兜底 */
    private String confidence;
}
