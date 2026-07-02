package com.example.sensitivedetection.classify2.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.classify2.model.FieldContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 检索词准备（fan-out 前的单入口）：
 * 用表级推断的 normalizedName 作主检索词；confidence=low 时追加原始中文名兜底。
 */
@Slf4j
@Component
public class QueryPrepV2Node implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) {
        FieldContext ctx = (FieldContext) state.value("result").orElseThrow();
        List<String> terms = new ArrayList<>();
        String norm = ctx.getNormalizedName();
        String original = ctx.getColumnChnName();

        if (norm != null && !norm.isBlank()) {
            terms.add(norm);
            boolean lowConf = !"high".equalsIgnoreCase(ctx.getConfidence());
            if (lowConf && original != null && !original.isBlank() && !original.equals(norm)) {
                terms.add(original);
            }
        } else if (original != null && !original.isBlank()) {
            terms.add(original);
        }
        ctx.setQueryTerms(terms);
        return Map.of("result", ctx);
    }
}
