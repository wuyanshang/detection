package com.example.sensitivedetection.rule;

import com.example.sensitivedetection.model.FieldResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A000004：同一表内字段中文名必须唯一
 */
@Component
@Order(4)
public class DuplicateFieldCnRule implements QualityRule {

    @Override
    public String getRuleCode() {
        return "A000004";
    }

    @Override
    public String getRuleDesc() {
        return "同一表内字段中文名必须唯一";
    }

    @Override
    public void check(List<FieldResult> results) {
        // 按 系统英文名+表英文名 分组
        Map<String, List<FieldResult>> grouped = results.stream()
                .collect(Collectors.groupingBy(
                        r -> safeStr(r.getSystemEn()) + "|" + safeStr(r.getTableEn())
                ));

        for (List<FieldResult> tableResults : grouped.values()) {
            // 统计每个非空中文名的出现次数
            Map<String, Long> countMap = tableResults.stream()
                    .map(FieldResult::getFieldCn)
                    .filter(cn -> cn != null && !cn.trim().isEmpty())
                    .collect(Collectors.groupingBy(String::trim, Collectors.counting()));

            // 找出重复的中文名
            Set<String> duplicateNames = countMap.entrySet().stream()
                    .filter(e -> e.getValue() > 1)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            // 标记所有重复字段
            if (!duplicateNames.isEmpty()) {
                for (FieldResult result : tableResults) {
                    String fieldCn = result.getFieldCn();
                    if (fieldCn != null && duplicateNames.contains(fieldCn.trim())) {
                        result.addQualityRule(getRuleCode(), getRuleDesc());
                    }
                }
            }
        }
    }

    private String safeStr(String s) {
        return s == null ? "" : s;
    }
}
