package com.example.sensitivedetection.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.model.FieldResult;
import com.example.sensitivedetection.sensitive.LlmSensitiveDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 步骤3：LLM 敏感识别（6 线程并发，仅处理关键词未决字段）
 */
@Slf4j
@Component
public class LlmDetectNode implements NodeAction {

    private static final int THREAD_COUNT = 6;
    private final LlmSensitiveDetector llmDetector;
    private final ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

    public LlmDetectNode(LlmSensitiveDetector llmDetector) {
        this.llmDetector = llmDetector;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<FieldResult> results = (List<FieldResult>) state.value("results").orElseThrow();

        List<FieldResult> undecided = results.stream()
                .filter(r -> r.getSensitiveSource() == null)
                .collect(Collectors.toList());

        log.info("步骤3: LLM 敏感识别, 待处理 {} 条, 线程数 {}", undecided.size(), THREAD_COUNT);

        if (undecided.isEmpty()) {
            return Map.of("results", results);
        }

        AtomicInteger processed = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(undecided.size());

        for (FieldResult result : undecided) {
            executor.submit(() -> {
                try {
                    llmDetector.detect(result);
                    int count = processed.incrementAndGet();
                    if (count % 50 == 0) {
                        log.info("  LLM 已处理 {}/{}", count, undecided.size());
                    }
                } catch (Exception e) {
                    log.error("  LLM 处理异常: {}.{} - {}",
                            result.getTableEn(), result.getFieldEn(), e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("LLM 并发处理被中断", e);
        }

        long llmSensitive = undecided.stream().filter(r -> "是".equals(r.getIsSuspectedSensitive())).count();
        log.info("  LLM 处理完成: 疑似敏感 {} 条, 非敏感 {} 条",
                llmSensitive, undecided.size() - llmSensitive);

        return Map.of("results", results);
    }
}
