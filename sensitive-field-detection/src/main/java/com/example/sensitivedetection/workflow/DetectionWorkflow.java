package com.example.sensitivedetection.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.sensitivedetection.model.DetectionResult;
import com.example.sensitivedetection.model.FieldInput;
import com.example.sensitivedetection.model.FieldResult;
import com.example.sensitivedetection.model.TableResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DetectionWorkflow {

    private final CompiledGraph<OverAllState> detectionGraph;

    public DetectionWorkflow(CompiledGraph<OverAllState> detectionGraph) {
        this.detectionGraph = detectionGraph;
    }

    @SuppressWarnings("unchecked")
    public DetectionResult execute(List<FieldInput> inputs) {
        String batchNo = generateBatchNo();
        log.info("========== 开始检测, 批次号: {}, 字段总数: {} ==========", batchNo, inputs.size());

        // 构建初始结果列表
        List<FieldResult> results = inputs.stream()
                .map(input -> FieldResult.from(input, batchNo))
                .collect(Collectors.toList());

        // 构建初始状态
        Map<String, Object> initialState = new HashMap<>();
        initialState.put("results", results);
        initialState.put("batchNo", batchNo);

        // 执行工作流图
        try {
            Optional<OverAllState> finalState = detectionGraph.invoke(initialState);
            if (finalState.isPresent()) {
                OverAllState state = finalState.get();
                List<FieldResult> fieldResults = (List<FieldResult>) state.value("results").orElse(results);
                List<TableResult> tableResults = (List<TableResult>) state.value("tableResults").orElse(Collections.emptyList());
                return new DetectionResult(fieldResults, tableResults);
            }
        } catch (Exception e) {
            log.error("工作流执行失败: {}", e.getMessage(), e);
            throw new RuntimeException("工作流执行失败", e);
        }

        return new DetectionResult(results, Collections.emptyList());
    }

    private String generateBatchNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }
}
