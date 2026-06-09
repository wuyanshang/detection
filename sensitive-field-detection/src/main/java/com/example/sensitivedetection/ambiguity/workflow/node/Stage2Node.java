package com.example.sensitivedetection.ambiguity.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.ambiguity.AmbiguityConstants;
import com.example.sensitivedetection.ambiguity.llm.AmbiguityLlmClient;
import com.example.sensitivedetection.ambiguity.llm.Stage2Response;
import com.example.sensitivedetection.ambiguity.model.Ambiguity;
import com.example.sensitivedetection.ambiguity.model.AmbiguityResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 步骤4：先按规则归属算出"待判规则集合"，再 stage2 按需加载判定（6 线程并发）。
 * - 理解类(B000001/B000003)：仅在 stage1 消歧失败时入选；
 * - 规范类(B000002/B000004)：由门控 hitB2/hitB4 入选，与消歧无关；
 * - 集合为空 → 门控直通，无歧义，不调 LLM。
 */
@Slf4j
@Component
public class Stage2Node implements NodeAction {

    private static final int THREAD_COUNT = 6;
    private final AmbiguityLlmClient llmClient;
    private final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

    public Stage2Node(AmbiguityLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<AmbiguityResult> results = (List<AmbiguityResult>) state.value("results").orElseThrow();

        // 1) 计算待判集合（确定性）
        for (AmbiguityResult r : results) {
            if (r.isCacheHit() || r.isSemanticDone()) {
                continue;
            }
            r.setRulesToCheck(computeRulesToCheck(r));
        }

        // 2) 集合为空的字段：门控直通，无歧义；非空的进 LLM
        List<AmbiguityResult> needLlm = new ArrayList<>();
        for (AmbiguityResult r : results) {
            if (r.isCacheHit() || r.isSemanticDone()) {
                continue;
            }
            if (r.getRulesToCheck().isEmpty()) {
                r.setHasAmbiguity(false);
                r.setSummary("门控直通，无歧义");
                r.setSource("门控直通");
                r.setSemanticDone(true);
            } else {
                needLlm.add(r);
            }
        }

        log.info("步骤4: stage2 待判 {} 条（其余门控直通无歧义）", needLlm.size());
        if (needLlm.isEmpty()) {
            return Map.of("results", results);
        }

        // 3) 并发调用 stage2
        CountDownLatch latch = new CountDownLatch(needLlm.size());
        for (AmbiguityResult r : needLlm) {
            executor.submit(() -> {
                try {
                    Stage2Response resp = llmClient.classify(r, r.getRulesToCheck());
                    applyStage2(r, resp);
                } catch (Exception e) {
                    log.warn("stage2 异常 {}.{}: {}", r.getTableEn(), r.getFieldEn(), e.getMessage());
                    r.setHasAmbiguity(false);
                    r.setSummary("stage2 异常，未判定");
                } finally {
                    r.setSource("LLM");
                    r.setSemanticDone(true);
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Map.of("results", results);
    }

    private Set<String> computeRulesToCheck(AmbiguityResult r) {
        Set<String> rules = new LinkedHashSet<>();
        // 理解类：仅消歧失败时
        if (Boolean.FALSE.equals(r.getDisambiguationSuccess())) {
            rules.add(AmbiguityConstants.B000001);
            rules.add(AmbiguityConstants.B000003);
        }
        // 规范类：门控命中即入选
        if (r.isGateB2()) {
            rules.add(AmbiguityConstants.B000002);
        }
        if (r.isGateB4()) {
            rules.add(AmbiguityConstants.B000004);
        }
        return rules;
    }

    private void applyStage2(AmbiguityResult r, Stage2Response resp) {
        if (resp == null || resp.getAmbiguities() == null) {
            r.setHasAmbiguity(false);
            r.setSummary("stage2 无返回，未判定");
            return;
        }
        for (Stage2Response.Item item : resp.getAmbiguities()) {
            // 只接受待判集合内的规则，防止越界乱报
            if (item.getRuleCode() != null && r.getRulesToCheck().contains(item.getRuleCode())) {
                r.addAmbiguity(new Ambiguity(item.getRuleCode(), item.getDetail(), item.getSuggestion()));
            }
        }
        r.setHasAmbiguity(!r.getAmbiguities().isEmpty());
        r.setSummary(resp.getSummary());
    }
}
