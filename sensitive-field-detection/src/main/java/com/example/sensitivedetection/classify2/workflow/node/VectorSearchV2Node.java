package com.example.sensitivedetection.classify2.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.classify2.es.CatalogSearchV2Service;
import com.example.sensitivedetection.classify2.model.FieldContext;
import com.example.sensitivedetection.classify2.model.SearchHitV2;
import com.example.sensitivedetection.security.embedding.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量检索，与 BM25 并行。写入独立键 vectorResults，不回写 result。
 * Embedding/ES 失败降级为空结果，不抛出以保证流程继续。
 */
@Slf4j
@Component
public class VectorSearchV2Node implements NodeAction {

    private final EmbeddingService embeddingService;
    private final CatalogSearchV2Service searchService;

    public VectorSearchV2Node(EmbeddingService embeddingService, CatalogSearchV2Service searchService) {
        this.embeddingService = embeddingService;
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
                float[] vec = embeddingService.embed(term);
                for (SearchHitV2 hit : searchService.vectorSearch(vec)) {
                    bestByCategory.merge(hit.getCategory(), hit,
                            (a, b) -> a.getScore() >= b.getScore() ? a : b);
                }
            } catch (Exception e) {
                log.warn("v2 向量检索降级（term='{}'）: {}", term, e.getMessage());
            }
        }
        return Map.of("vectorResults", new ArrayList<>(bestByCategory.values()));
    }
}
