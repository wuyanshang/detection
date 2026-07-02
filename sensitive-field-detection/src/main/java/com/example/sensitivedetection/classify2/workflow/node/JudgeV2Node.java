package com.example.sensitivedetection.classify2.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.classify2.llm.FieldJudgeClient;
import com.example.sensitivedetection.classify2.llm.FieldJudgeResponse;
import com.example.sensitivedetection.classify2.model.CandidateV2;
import com.example.sensitivedetection.classify2.model.FieldContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 判定：候选带 content/定义/路径 + 主体一致性。
 * 候选为空 / LLM 失败 → category=null。命中后回填 securityLevel（优先取候选目录值）。
 */
@Slf4j
@Component
public class JudgeV2Node implements NodeAction {

    private final FieldJudgeClient judgeClient;

    public JudgeV2Node(FieldJudgeClient judgeClient) {
        this.judgeClient = judgeClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        FieldContext ctx = (FieldContext) state.value("result").orElseThrow();

        if (ctx.getCandidates() == null || ctx.getCandidates().isEmpty()) {
            ctx.setCategory(null);
            ctx.setReason("无候选，未匹配到目录");
            ctx.setSource("no-candidate");
            return Map.of("result", ctx);
        }

        FieldJudgeResponse resp = judgeClient.judge(ctx);
        if (resp == null) {
            ctx.setCategory(null);
            ctx.setReason("LLM 判定失败，按未匹配处理");
            ctx.setSource("degraded");
            return Map.of("result", ctx);
        }

        String category = blankToNull(resp.getCategory());
        ctx.setCategory(category);
        ctx.setMatchedCatalog(blankToNull(resp.getMatchedCatalog()));
        ctx.setSubject(blankToNull(resp.getSubject()));
        ctx.setReason(resp.getReason());
        ctx.setSource("llm");
        ctx.setSecurityLevel(resolveSecurityLevel(ctx, category, resp.getSecurityLevel()));
        return Map.of("result", ctx);
    }

    /** 安全级别以命中目录的值为准，取不到再用 LLM 返回值。 */
    private Integer resolveSecurityLevel(FieldContext ctx, String category, Integer llmValue) {
        if (category != null) {
            for (CandidateV2 c : ctx.getCandidates()) {
                if (category.equals(c.getCategory()) && c.getSecurityLevel() != null) {
                    return c.getSecurityLevel();
                }
            }
        }
        return llmValue;
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
