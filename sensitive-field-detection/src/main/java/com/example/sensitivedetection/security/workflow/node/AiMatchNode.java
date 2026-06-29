package com.example.sensitivedetection.security.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.security.llm.SecurityMatchLlmClient;
import com.example.sensitivedetection.security.llm.SecurityMatchResponse;
import com.example.sensitivedetection.security.model.SecurityClassificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 判定（对应设计文档 8.3.6）。
 * 候选为空 / LLM 失败 → category=null（未匹配）。
 */
@Slf4j
@Component
public class AiMatchNode implements NodeAction {

    private final SecurityMatchLlmClient llmClient;

    public AiMatchNode(SecurityMatchLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        SecurityClassificationResult r = (SecurityClassificationResult) state.value("result").orElseThrow();
        if (r.isExempt()) {
            return Map.of("result", r);
        }

        if (r.getCandidates() == null || r.getCandidates().isEmpty()) {
            r.setCategory(null);
            r.setMatchedCatalog(null);
            r.setReason("无候选，未匹配到目录");
            r.setSource("no-candidate");
            return Map.of("result", r);
        }

        SecurityMatchResponse resp = llmClient.classify(r);
        if (resp == null) {
            r.setCategory(null);
            r.setMatchedCatalog(null);
            r.setReason("LLM 判定失败，按未匹配处理");
            r.setSource("degraded");
            return Map.of("result", r);
        }

        r.setCategory(blankToNull(resp.getCategory()));
        r.setMatchedCatalog(blankToNull(resp.getMatchedCatalog()));
        r.setReason(resp.getReason());
        r.setSource("llm");
        return Map.of("result", r);
    }

    private String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty() || "null".equalsIgnoreCase(t)) {
            return null;
        }
        return t;
    }
}
