package com.example.sensitivedetection.classify2.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.classify2.model.CandidateV2;
import com.example.sensitivedetection.classify2.model.FieldContext;
import com.example.sensitivedetection.classify2.model.SearchHitV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合并去重（fan-in 汇聚点）：按 category 合并两路结果，携带 content/定义/安全级别，回写 result。
 */
@Slf4j
@Component
public class MergeV2Node implements NodeAction {

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        FieldContext ctx = (FieldContext) state.value("result").orElseThrow();
        List<SearchHitV2> vectorResults = (List<SearchHitV2>) state.value("vectorResults")
                .orElse(new ArrayList<SearchHitV2>());
        List<SearchHitV2> bm25Results = (List<SearchHitV2>) state.value("bm25Results")
                .orElse(new ArrayList<SearchHitV2>());
        ctx.setVectorResults(vectorResults);
        ctx.setBm25Results(bm25Results);

        Map<String, CandidateV2> byCategory = new LinkedHashMap<>();
        for (SearchHitV2 hit : vectorResults) {
            CandidateV2 c = byCategory.computeIfAbsent(hit.getCategory(), k -> newItem(hit));
            c.setVectorScore(Math.max(c.getVectorScore(), hit.getScore()));
            c.setSource(mergeSource(c.getSource(), "vector"));
        }
        for (SearchHitV2 hit : bm25Results) {
            CandidateV2 c = byCategory.computeIfAbsent(hit.getCategory(), k -> newItem(hit));
            c.setBm25Score(Math.max(c.getBm25Score(), hit.getScore()));
            c.setSource(mergeSource(c.getSource(), "bm25"));
        }

        List<CandidateV2> candidates = new ArrayList<>(byCategory.values());
        ctx.setCandidates(candidates);
        log.debug("v2 合并去重: 向量 {} + BM25 {} → 候选 {}",
                vectorResults.size(), bm25Results.size(), candidates.size());
        return Map.of("result", ctx);
    }

    private CandidateV2 newItem(SearchHitV2 hit) {
        CandidateV2 c = new CandidateV2();
        c.setCategory(hit.getCategory());
        c.setAsset(hit.getAsset());
        c.setContent(hit.getContent());
        c.setLevel3Definition(hit.getLevel3Definition());
        c.setSecurityLevel(hit.getSecurityLevel());
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
