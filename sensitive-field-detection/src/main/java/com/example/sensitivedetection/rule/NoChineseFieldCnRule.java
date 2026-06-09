package com.example.sensitivedetection.rule;

import com.example.sensitivedetection.model.FieldResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A000002：字段中文名需包含中文描述
 */
@Component
@Order(3)
public class NoChineseFieldCnRule implements QualityRule {

    private static final Pattern CHINESE_CHAR = Pattern.compile("[\\u4e00-\\u9fff]");

    @Override
    public String getRuleCode() {
        return "A000002";
    }

    @Override
    public String getRuleDesc() {
        return "字段中文名需包含中文描述";
    }

    @Override
    public void check(List<FieldResult> results) {
        for (FieldResult result : results) {
            String fieldCn = result.getFieldCn();
            if (fieldCn == null || fieldCn.trim().isEmpty()) {
                continue; // A000003 已处理
            }
            if (!CHINESE_CHAR.matcher(fieldCn).find()) {
                result.addQualityRule(getRuleCode(), getRuleDesc());
            }
        }
    }
}
