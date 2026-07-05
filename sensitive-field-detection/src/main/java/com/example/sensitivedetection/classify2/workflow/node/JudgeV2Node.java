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
 * 输出 matchType：EXACT（精确命中）/ FALLBACK（选候选中语义最接近者，待复核）/ UNMATCHED（无相关候选或候选为空）。
 * category 必须来自候选（否则判 UNMATCHED）；securityLevel/regulatoryLevel 始终取自命中候选的真实值。
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
            ctx.setMatchType("UNMATCHED");
            ctx.setReason("无候选，未匹配到目录");
            ctx.setSource("no-candidate");
            return Map.of("result", ctx);
        }

        FieldJudgeResponse resp = judgeClient.judge(ctx);
        if (resp == null) {
            ctx.setCategory(null);
            ctx.setMatchType("UNMATCHED");
            ctx.setReason("LLM 判定失败，按未匹配处理");
            ctx.setSource("degraded");
            return Map.of("result", ctx);
        }

        String category = blankToNull(resp.getCategory());
        CandidateV2 hit = findCandidate(ctx, category);
        ctx.setSource("llm");
        ctx.setSubject(blankToNull(resp.getSubject()));
        ctx.setReason(resp.getReason());

        // category 必须来自候选；LLM 若返回不在候选中的 category 视为无效 → UNMATCHED
        if (category == null || hit == null) {
            ctx.setCategory(null);
            ctx.setMatchedCatalog(null);
            ctx.setSecurityLevel(null);
            ctx.setRegulatoryLevel(null);
            ctx.setMatchType("UNMATCHED");
            if (category != null && hit == null) {
                ctx.setReason(nz(resp.getReason()) + "（LLM 返回的 category 不在候选中，按未匹配处理）");
            }
            return Map.of("result", ctx);
        }

        ctx.setCategory(category);
        ctx.setMatchedCatalog(hit.getAsset());
        // 级别始终取自命中候选的真实值，不估算、不拼造
        ctx.setSecurityLevel(hit.getSecurityLevel());
        ctx.setRegulatoryLevel(hit.getRegulatoryLevel());
        ctx.setMatchType(normalizeMatchType(resp.getMatchType()));
        return Map.of("result", ctx);
    }

    /** 规整 LLM 返回的 match_type；命中候选时缺省视为 EXACT。 */
    private String normalizeMatchType(String raw) {
        String t = raw == null ? "" : raw.trim().toUpperCase();
        if ("FALLBACK".equals(t)) {
            return "FALLBACK";
        }
        if ("UNMATCHED".equals(t)) {
            // 已确认 category 在候选中，UNMATCHED 与之矛盾，保守降级为 FALLBACK 待复核
            return "FALLBACK";
        }
        return "EXACT";
    }

    /** 按 category 在候选中定位命中项（EXACT 校验的核心：category 必须真实存在）。 */
    private CandidateV2 findCandidate(FieldContext ctx, String category) {
        if (category == null) {
            return null;
        }
        for (CandidateV2 c : ctx.getCandidates()) {
            if (category.equals(c.getCategory())) {
                return c;
            }
        }
        return null;
    }

    private String nz(String s) {
        return s == null ? "" : s;
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
