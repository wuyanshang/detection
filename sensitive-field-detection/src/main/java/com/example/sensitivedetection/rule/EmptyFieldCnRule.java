package com.example.sensitivedetection.rule;

import com.example.sensitivedetection.model.FieldResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A000003：字段中文名禁止为空
 */
@Component
@Order(1)
public class EmptyFieldCnRule implements QualityRule {

    @Override
    public String getRuleCode() {
        return "A000003";
    }

    @Override
    public String getRuleDesc() {
        return "字段中文名禁止为空";
    }

    @Override
    public void check(List<FieldResult> results) {
        for (FieldResult result : results) {
            String fieldCn = result.getFieldCn();
            if (fieldCn == null || fieldCn.trim().isEmpty()) {
                result.addQualityRule(getRuleCode(), getRuleDesc());
            }
        }
    }
}
