package com.example.sensitivedetection.ambiguity.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条歧义命中（规则码 + 描述 + 建议）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ambiguity {

    private String ruleCode;   // B000001 ~ B000005
    private String detail;
    private String suggestion;
}
