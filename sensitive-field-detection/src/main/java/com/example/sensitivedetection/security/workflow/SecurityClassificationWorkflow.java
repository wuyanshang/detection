package com.example.sensitivedetection.security.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.sensitivedetection.security.config.SecurityClassificationProperties;
import com.example.sensitivedetection.security.dto.SecurityClassificationInputDTO;
import com.example.sensitivedetection.security.model.SecurityClassificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 安全分类分级工作流入口。单条同步执行；批量受 llm-concurrency-limit 限流并发。
 */
@Slf4j
@Component
public class SecurityClassificationWorkflow {

    private final CompiledGraph graph;
    private final SecurityClassificationProperties props;

    public SecurityClassificationWorkflow(
            @Qualifier("securityClassificationGraph") CompiledGraph graph,
            SecurityClassificationProperties props) {
        this.graph = graph;
        this.props = props;
    }

    /** 单条分类分级。 */
    public SecurityClassificationResult classify(SecurityClassificationInputDTO input) {
        SecurityClassificationResult result = SecurityClassificationResult.from(input);
        Map<String, Object> initialState = new HashMap<>();
        initialState.put("result", result);
        try {
            Optional<OverAllState> finalState = graph.invoke(initialState);
            if (finalState.isPresent()) {
                return (SecurityClassificationResult) finalState.get().value("result").orElse(result);
            }
        } catch (Exception e) {
            log.error("安全分级工作流执行失败 {}: {}", result.getCacheKey(), e.getMessage(), e);
            throw new RuntimeException("安全分级工作流执行失败", e);
        }
        return result;
    }

    /** 批量分类分级：受 llm-concurrency-limit 限流并发。 */
    public List<SecurityClassificationResult> classifyBatch(List<SecurityClassificationInputDTO> inputs) {
        int concurrency = Math.max(1, props.getLlmConcurrencyLimit());
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<SecurityClassificationResult>> futures = new ArrayList<>(inputs.size());
            for (SecurityClassificationInputDTO in : inputs) {
                futures.add(executor.submit(() -> classifyQuietly(in)));
            }
            List<SecurityClassificationResult> results = new ArrayList<>(inputs.size());
            for (Future<SecurityClassificationResult> f : futures) {
                try {
                    results.add(f.get(props.getLatchTimeoutMinutes(), TimeUnit.MINUTES));
                } catch (Exception e) {
                    log.error("批量分级单条失败: {}", e.getMessage());
                }
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }

    private SecurityClassificationResult classifyQuietly(SecurityClassificationInputDTO in) {
        try {
            return classify(in);
        } catch (Exception e) {
            SecurityClassificationResult r = SecurityClassificationResult.from(in);
            r.setCategory(null);
            r.setReason("执行失败: " + e.getMessage());
            r.setSource("error");
            return r;
        }
    }
}
