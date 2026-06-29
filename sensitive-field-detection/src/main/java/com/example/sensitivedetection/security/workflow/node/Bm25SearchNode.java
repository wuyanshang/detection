package com.example.sensitivedetection.security.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.security.es.ElasticsearchService;
import com.example.sensitivedetection.security.model.SearchResult;
import com.example.sensitivedetection.security.model.SecurityClassificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BM25 关键词检索（对应设计文档 8.3.4），与向量检索并行执行。
 *
 * 并行约定：只读 result（取 queryTerms / isExempt），结果写入独立键 "bm25Results"，
 * 不回写 result，避免与并行的向量节点竞态。
 * ES 失败 → 降级（结果为空）；不抛出以保证流程继续。
 */
@Slf4j
@Component
public class Bm25SearchNode implements NodeAction {

    private final ElasticsearchService esService;

    public Bm25SearchNode(ElasticsearchService esService) {
        this.esService = esService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        SecurityClassificationResult r = (SecurityClassificationResult) state.value("result").orElseThrow();
        if (r.isExempt()) {
            return Map.of("bm25Results", new ArrayList<SearchResult>());
        }

        Map<String, SearchResult> bestByCategory = new LinkedHashMap<>();
        for (String term : r.getQueryTerms()) {
            if (term == null || term.isBlank()) {
                continue;
            }
            try {
                for (SearchResult sr : esService.bm25Search(term)) {
                    bestByCategory.merge(sr.getCategory(), sr,
                            (a, b) -> a.getScore() >= b.getScore() ? a : b);
                }
            } catch (Exception e) {
                log.warn("BM25 检索降级（term='{}'）: {}", term, e.getMessage());
            }
        }
        return Map.of("bm25Results", new ArrayList<>(bestByCategory.values()));
    }
}
