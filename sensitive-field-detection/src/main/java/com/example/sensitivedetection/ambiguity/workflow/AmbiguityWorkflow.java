package com.example.sensitivedetection.ambiguity.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.sensitivedetection.ambiguity.AmbiguityConstants;
import com.example.sensitivedetection.ambiguity.model.AmbiguityResult;
import com.example.sensitivedetection.model.FieldInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AmbiguityWorkflow {

    private final CompiledGraph<OverAllState> ambiguityGraph;

    public AmbiguityWorkflow(@Qualifier("ambiguityGraph") CompiledGraph<OverAllState> ambiguityGraph) {
        this.ambiguityGraph = ambiguityGraph;
    }

    @SuppressWarnings("unchecked")
    public List<AmbiguityResult> execute(List<FieldInput> inputs) {
        String batchNo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        log.info("========== 歧义检测开始, 批次: {}, 字段数: {} ==========", batchNo, inputs.size());

        List<AmbiguityResult> results = inputs.stream()
                .map(in -> AmbiguityResult.from(in, batchNo, AmbiguityConstants.PROMPT_VERSION))
                .collect(Collectors.toList());

        Map<String, Object> initialState = new HashMap<>();
        initialState.put("results", results);
        initialState.put("batchNo", batchNo);

        try {
            Optional<OverAllState> finalState = ambiguityGraph.invoke(initialState);
            if (finalState.isPresent()) {
                return (List<AmbiguityResult>) finalState.get().value("results").orElse(results);
            }
        } catch (Exception e) {
            log.error("歧义检测工作流执行失败: {}", e.getMessage(), e);
            throw new RuntimeException("歧义检测工作流执行失败", e);
        }
        return results;
    }
}
