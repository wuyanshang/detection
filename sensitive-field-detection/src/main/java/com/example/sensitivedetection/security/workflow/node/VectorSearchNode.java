package com.example.sensitivedetection.security.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.security.embedding.EmbeddingService;
import com.example.sensitivedetection.security.es.ElasticsearchService;
import com.example.sensitivedetection.security.model.SearchResult;
import com.example.sensitivedetection.security.model.SecurityClassificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量检索（对应设计文档 8.3.3），与 BM25 并行执行。
 *
 * 并行约定：只读 result（取 queryTerms / isExempt），结果写入独立键 "vectorResults"，
 * 不回写 result，避免与并行的 BM25 节点竞态。
 * Embedding 失败 → 降级（向量结果为空，仅靠 BM25）；不抛出以保证流程继续。
 */
@Slf4j
@Component
public class VectorSearchNode implements NodeAction {

    private final EmbeddingService embeddingService;
    private final ElasticsearchService esService;

    public VectorSearchNode(EmbeddingService embeddingService, ElasticsearchService esService) {
        this.embeddingService = embeddingService;
        this.esService = esService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        SecurityClassificationResult r = (SecurityClassificationResult) state.value("result").orElseThrow();
        if (r.isExempt()) {
            return Map.of("vectorResults", new ArrayList<SearchResult>());
        }

        Map<String, SearchResult> bestByCategory = new LinkedHashMap<>();
        for (String term : r.getQueryTerms()) {
            if (term == null || term.isBlank()) {
                continue;
            }
            try {
                float[] vec = embeddingService.embed(term);
                for (SearchResult sr : esService.vectorSearch(vec)) {
                    bestByCategory.merge(sr.getCategory(), sr,
                            (a, b) -> a.getScore() >= b.getScore() ? a : b);
                }
            } catch (Exception e) {
                log.warn("向量检索降级（term='{}'）: {}", term, e.getMessage());
            }
        }
        return Map.of("vectorResults", new ArrayList<>(bestByCategory.values()));
    }
}
