package com.example.sensitivedetection.classify2.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.classify2.es.CatalogSearchV2Service;
import com.example.sensitivedetection.classify2.model.FieldContext;
import com.example.sensitivedetection.classify2.model.SearchHitV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BM25 检索（multi_match: asset/content/example），与向量检索并行。
 * 写入独立键 bm25Results，不回写 result。ES 失败降级为空。
 */
@Slf4j
@Component
public class Bm25SearchV2Node implements NodeAction {

    private final CatalogSearchV2Service searchService;

    public Bm25SearchV2Node(CatalogSearchV2Service searchService) {
        this.searchService = searchService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        FieldContext ctx = (FieldContext) state.value("result").orElseThrow();
        Map<String, SearchHitV2> bestByCategory = new LinkedHashMap<>();
        for (String term : ctx.getQueryTerms()) {
            if (term == null || term.isBlank()) {
                continue;
            }
            try {
                for (SearchHitV2 hit : searchService.bm25Search(term)) {
                    bestByCategory.merge(hit.getCategory(), hit,
                            (a, b) -> a.getScore() >= b.getScore() ? a : b);
                }
            } catch (Exception e) {
                log.warn("v2 BM25 检索降级（term='{}'）: {}", term, e.getMessage());
            }
        }
        return Map.of("bm25Results", new ArrayList<>(bestByCategory.values()));
    }
}
