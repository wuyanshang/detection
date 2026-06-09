package com.example.sensitivedetection.rule;

import com.example.sensitivedetection.model.FieldResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A000001：字段中文名含乱码
 */
@Component
@Order(2)
public class GarbledFieldCnRule implements QualityRule {

    // 编码损坏型乱码（UTF-8 被当 Latin-1 解析）
    private static final Pattern ENCODING_GARBLED = Pattern.compile("[\\u00c0-\\u00ff]{2,}");
    // 不可见控制字符
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]");
    // 私有使用区字符
    private static final Pattern PRIVATE_USE = Pattern.compile("[\\uE000-\\uF8FF]");
    // Unicode 替换字符
    private static final String REPLACEMENT_CHAR = "\uFFFD";

    @Override
    public String getRuleCode() {
        return "A000001";
    }

    @Override
    public String getRuleDesc() {
        return "字段中文名含乱码";
    }

    @Override
    public void check(List<FieldResult> results) {
        for (FieldResult result : results) {
            String fieldCn = result.getFieldCn();
            if (fieldCn == null || fieldCn.trim().isEmpty()) {
                continue; // A000003 已处理
            }
            if (containsGarbled(fieldCn)) {
                result.addQualityRule(getRuleCode(), getRuleDesc());
            }
        }
    }

    private boolean containsGarbled(String text) {
        if (ENCODING_GARBLED.matcher(text).find()) return true;
        if (text.contains(REPLACEMENT_CHAR)) return true;
        if (CONTROL_CHARS.matcher(text).find()) return true;
        if (PRIVATE_USE.matcher(text).find()) return true;
        return false;
    }
}
