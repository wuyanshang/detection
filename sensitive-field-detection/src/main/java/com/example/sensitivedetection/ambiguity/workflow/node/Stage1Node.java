package com.example.sensitivedetection.ambiguity.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.ambiguity.llm.AmbiguityLlmClient;
import com.example.sensitivedetection.ambiguity.llm.Stage1Response;
import com.example.sensitivedetection.ambiguity.model.AmbiguityResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 步骤3：stage1 消歧（仅对未命中缓存、非备用的字段，6 线程并发）。
 * 用于决定理解类规则(B000001/B000003)是否进入 stage2。
 */
@Slf4j
@Component
public class Stage1Node implements NodeAction {

    private static final int THREAD_COUNT = 6;
    private final AmbiguityLlmClient llmClient;
    private final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

    public Stage1Node(AmbiguityLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<AmbiguityResult> results = (List<AmbiguityResult>) state.value("results").orElseThrow();

        List<AmbiguityResult> todo = results.stream()
                .filter(r -> !r.isCacheHit() && !r.isSemanticDone())
                .collect(Collectors.toList());

        log.info("步骤3: stage1 消歧，待处理 {} 条", todo.size());
        if (todo.isEmpty()) {
            return Map.of("results", results);
        }

        CountDownLatch latch = new CountDownLatch(todo.size());
        for (AmbiguityResult r : todo) {
            executor.submit(() -> {
                try {
                    Stage1Response resp = llmClient.disambiguate(r);
                    if (resp == null) {
                        // 多次失败：按消歧失败处理，进入 stage2 兜底判定
                        r.setDisambiguationSuccess(false);
                        r.setDisambiguationPath("stage1 调用失败，按消歧失败处理");
                    } else {
                        r.setDisambiguationSuccess(resp.isDisambiguationSuccess());
                        r.setDisambiguationPath(resp.getDisambiguationPath());
                        if (r.getSummary() == null) {
                            r.setSummary(resp.getSummary());
                        }
                    }
                } catch (Exception e) {
                    r.setDisambiguationSuccess(false);
                    r.setDisambiguationPath("stage1 异常: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long fail = todo.stream().filter(r -> Boolean.FALSE.equals(r.getDisambiguationSuccess())).count();
        log.info("步骤3: stage1 完成，消歧成功 {} 条，消歧失败 {} 条", todo.size() - fail, fail);
        return Map.of("results", results);
    }
}
