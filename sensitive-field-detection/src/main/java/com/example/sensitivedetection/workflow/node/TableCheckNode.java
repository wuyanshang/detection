package com.example.sensitivedetection.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.model.FieldResult;
import com.example.sensitivedetection.model.TableResult;
import com.example.sensitivedetection.rule.TableRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 表级质检规则（E000001~E000003），在字段质检之前执行
 * 从字段列表中提取去重的表信息，生成 TableResult 列表
 */
@Slf4j
@Component
public class TableCheckNode implements NodeAction {

    private final List<TableRule> tableRules;

    public TableCheckNode(List<TableRule> tableRules) {
        this.tableRules = tableRules;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<FieldResult> fieldResults = (List<FieldResult>) state.value("results").orElseThrow();
        String batchNo = (String) state.value("batchNo").orElse("");

        // 按 系统英文名+表英文名 去重，提取表信息
        Map<String, TableResult> tableMap = new LinkedHashMap<>();
        for (FieldResult fr : fieldResults) {
            String key = safeStr(fr.getSystemEn()) + "|" + safeStr(fr.getTableEn());
            tableMap.computeIfAbsent(key, k -> {
                TableResult tr = new TableResult();
                tr.setBatchNo(batchNo);
                tr.setSystemCn(fr.getSystemCn());
                tr.setSystemEn(fr.getSystemEn());
                tr.setTableCn(fr.getTableCn());
                tr.setTableEn(fr.getTableEn());
                return tr;
            });
        }
        List<TableResult> tableResults = new ArrayList<>(tableMap.values());

        log.info("步骤0: 执行表级质检规则 (E000001~E000003), 表数量: {}", tableResults.size());
        for (TableRule rule : tableRules) {
            rule.check(tableResults);
            long count = tableResults.stream().filter(t -> t.getRuleCodes().contains(rule.getRuleCode())).count();
            log.info("  {} - {} : 触发 {} 条", rule.getRuleCode(), rule.getRuleDesc(), count);
        }
        long intercepted = tableResults.stream().filter(t -> !t.getRuleCodes().isEmpty()).count();
        log.info("  表级质检完成: 拦截 {} 条, 通过 {} 条", intercepted, tableResults.size() - intercepted);

        return Map.of("results", fieldResults, "tableResults", tableResults);
    }

    private String safeStr(String s) {
        return s == null ? "" : s;
    }
}
