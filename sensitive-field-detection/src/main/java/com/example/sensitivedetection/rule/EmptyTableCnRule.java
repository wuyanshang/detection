package com.example.sensitivedetection.rule;

import com.example.sensitivedetection.model.TableResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * E000001：表中文名禁止为空
 */
@Component
@Order(1)
public class EmptyTableCnRule implements TableRule {

    @Override
    public String getRuleCode() {
        return "E000001";
    }

    @Override
    public String getRuleDesc() {
        return "表中文名禁止为空";
    }

    @Override
    public void check(List<TableResult> results) {
        for (TableResult result : results) {
            String tableCn = result.getTableCn();
            if (tableCn == null || tableCn.trim().isEmpty()) {
                result.addRule(getRuleCode(), getRuleDesc());
            }
        }
    }
}
