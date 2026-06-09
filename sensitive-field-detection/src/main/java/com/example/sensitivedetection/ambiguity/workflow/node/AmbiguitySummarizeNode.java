package com.example.sensitivedetection.ambiguity.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.ambiguity.AmbiguityConstants;
import com.example.sensitivedetection.ambiguity.model.AmbiguityResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 步骤7：汇总统计。
 */
@Slf4j
@Component
public class AmbiguitySummarizeNode implements NodeAction {

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<AmbiguityResult> results = (List<AmbiguityResult>) state.value("results").orElseThrow();

        long total = results.size();
        long cacheHit = results.stream().filter(AmbiguityResult::isCacheHit).count();
        long ambiguous = results.stream().filter(AmbiguityResult::isHasAmbiguity).count();
        long b1 = countRule(results, AmbiguityConstants.B000001);
        long b2 = countRule(results, AmbiguityConstants.B000002);
        long b3 = countRule(results, AmbiguityConstants.B000003);
        long b4 = countRule(results, AmbiguityConstants.B000004);
        long b5 = countRule(results, AmbiguityConstants.B000005);

        log.info("========== 歧义检测完成 ==========");
        log.info("  总计: {} 条，缓存命中: {} 条", total, cacheHit);
        log.info("  有歧义: {} 条，无歧义: {} 条", ambiguous, total - ambiguous);
        log.info("  B000001={}, B000002={}, B000003={}, B000004={}, B000005={}", b1, b2, b3, b4, b5);
        return Map.of("results", results);
    }

    private long countRule(List<AmbiguityResult> results, String code) {
        return results.stream().filter(r -> r.hasRule(code)).count();
    }
}
