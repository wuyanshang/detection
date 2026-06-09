package com.example.sensitivedetection.rule;

import com.example.sensitivedetection.model.FieldResult;

import java.util.List;

public interface QualityRule {

    /**
     * 规则编号
     */
    String getRuleCode();

    /**
     * 规则描述
     */
    String getRuleDesc();

    /**
     * 对字段列表执行检查，触发的规则会自动追加到 FieldResult 中
     */
    void check(List<FieldResult> results);
}
