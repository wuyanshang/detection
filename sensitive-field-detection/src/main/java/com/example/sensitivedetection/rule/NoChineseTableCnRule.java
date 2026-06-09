package com.example.sensitivedetection.rule;

import com.example.sensitivedetection.model.TableResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * E000002：表中文名要包含中文描述
 */
@Component
@Order(2)
public class NoChineseTableCnRule implements TableRule {

    private static final Pattern CHINESE_CHAR = Pattern.compile("[\\u4e00-\\u9fff]");

    @Override
    public String getRuleCode() {
        return "E000002";
    }

    @Override
    public String getRuleDesc() {
        return "表中文名要包含中文描述";
    }

    @Override
    public void check(List<TableResult> results) {
        for (TableResult result : results) {
            String tableCn = result.getTableCn();
            if (tableCn == null || tableCn.trim().isEmpty()) {
                continue; // E000001 已处理
            }
            if (!CHINESE_CHAR.matcher(tableCn).find()) {
                result.addRule(getRuleCode(), getRuleDesc());
            }
        }
    }
}
