package com.example.sensitivedetection.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.model.FieldResult;
import com.example.sensitivedetection.rule.QualityRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 步骤1：质检规则（A000001~A000004）
 */
@Slf4j
@Component
public class QualityCheckNode implements NodeAction {

    private final List<QualityRule> qualityRules;

    public QualityCheckNode(List<QualityRule> qualityRules) {
        this.qualityRules = qualityRules;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<FieldResult> results = (List<FieldResult>) state.value("results").orElseThrow();

        log.info("步骤1: 执行质检规则 (A000001~A000004)");
        for (QualityRule rule : qualityRules) {
            rule.check(results);
            long count = results.stream()
                    .filter(r -> r.getRuleCodes().contains(rule.getRuleCode()))
                    .count();
            log.info("  {} - {} : 触发 {} 条", rule.getRuleCode(), rule.getRuleDesc(), count);
        }
        results.forEach(FieldResult::computeQualityResult);
        long intercepted = results.stream().filter(r -> "拦截".equals(r.getQualityResult())).count();
        log.info("  质检完成: 拦截 {} 条, 通过 {} 条", intercepted, results.size() - intercepted);

        return Map.of("results", results);
    }
}
