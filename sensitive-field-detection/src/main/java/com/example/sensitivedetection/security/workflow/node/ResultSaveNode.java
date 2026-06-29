package com.example.sensitivedetection.security.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.security.model.SecurityClassificationResult;
import com.example.sensitivedetection.security.repository.SecurityClassificationResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 结果保存（对应设计文档 8.3.7）。
 * 免检命中直接返回（不重复写库）；否则 UPSERT。
 */
@Slf4j
@Component
public class ResultSaveNode implements NodeAction {

    private final SecurityClassificationResultRepository repository;

    public ResultSaveNode(SecurityClassificationResultRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        SecurityClassificationResult r = (SecurityClassificationResult) state.value("result").orElseThrow();
        if (r.isExempt() || !repository.enabled()) {
            return Map.of("result", r);
        }
        try {
            repository.save(r);
        } catch (Exception e) {
            log.error("保存分级结果失败 {}: {}", r.getCacheKey(), e.getMessage());
        }
        return Map.of("result", r);
    }
}
