package com.example.sensitivedetection.security.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.security.model.CandidateItem;
import com.example.sensitivedetection.security.model.SearchResult;
import com.example.sensitivedetection.security.model.SecurityClassificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合并去重（对应设计文档 8.3.5 / 5.3.3），并行两路的 fan-in 汇聚点。
 * 从独立键 vectorResults / bm25Results 读取两路结果，按 category 去重，
 * 保留各自 score 与来源标记（vector/bm25/both），并回写到 result。
 */
@Slf4j
@Component
public class MergeDedupNode implements NodeAction {

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        SecurityClassificationResult r = (SecurityClassificationResult) state.value("result").orElseThrow();
        if (r.isExempt()) {
            return Map.of("result", r);
        }

        List<SearchResult> vectorResults = (List<SearchResult>) state.value("vectorResults")
                .orElse(new ArrayList<SearchResult>());
        List<SearchResult> bm25Results = (List<SearchResult>) state.value("bm25Results")
                .orElse(new ArrayList<SearchResult>());
        r.setVectorResults(vectorResults);
        r.setBm25Results(bm25Results);

        Map<String, CandidateItem> byCategory = new LinkedHashMap<>();

        for (SearchResult sr : vectorResults) {
            CandidateItem c = byCategory.computeIfAbsent(sr.getCategory(), k -> newItem(sr));
            c.setVectorScore(Math.max(c.getVectorScore(), sr.getScore()));
            c.setSource(mergeSource(c.getSource(), "vector"));
        }
        for (SearchResult sr : bm25Results) {
            CandidateItem c = byCategory.computeIfAbsent(sr.getCategory(), k -> newItem(sr));
            c.setBm25Score(Math.max(c.getBm25Score(), sr.getScore()));
            c.setSource(mergeSource(c.getSource(), "bm25"));
        }

        List<CandidateItem> candidates = new ArrayList<>(byCategory.values());
        r.setCandidates(candidates);
        log.debug("合并去重: 向量 {} + BM25 {} → 候选 {}",
                vectorResults.size(), bm25Results.size(), candidates.size());
        return Map.of("result", r);
    }

    private CandidateItem newItem(SearchResult sr) {
        CandidateItem c = new CandidateItem();
        c.setCategory(sr.getCategory());
        c.setAsset(sr.getAsset());
        return c;
    }

    private String mergeSource(String existing, String incoming) {
        if (existing == null) {
            return incoming;
        }
        if (existing.equals(incoming)) {
            return existing;
        }
        return "both";
    }
}
