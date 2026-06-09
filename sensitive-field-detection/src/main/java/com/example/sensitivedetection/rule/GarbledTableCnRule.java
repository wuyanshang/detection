package com.example.sensitivedetection.rule;

import com.example.sensitivedetection.model.TableResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * E000003：表中文名为乱码
 */
@Component
@Order(3)
public class GarbledTableCnRule implements TableRule {

    private static final Pattern ENCODING_GARBLED = Pattern.compile("[\\u00c0-\\u00ff]{2,}");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]");
    private static final Pattern PRIVATE_USE = Pattern.compile("[\\uE000-\\uF8FF]");
    private static final String REPLACEMENT_CHAR = "\uFFFD";

    @Override
    public String getRuleCode() {
        return "E000003";
    }

    @Override
    public String getRuleDesc() {
        return "表中文名为乱码";
    }

    @Override
    public void check(List<TableResult> results) {
        for (TableResult result : results) {
            String tableCn = result.getTableCn();
            if (tableCn == null || tableCn.trim().isEmpty()) {
                continue; // E000001 已处理
            }
            if (containsGarbled(tableCn)) {
                result.addRule(getRuleCode(), getRuleDesc());
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
