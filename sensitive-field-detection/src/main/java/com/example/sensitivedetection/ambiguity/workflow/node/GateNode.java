package com.example.sensitivedetection.ambiguity.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.ambiguity.gate.AmbiguityGate;
import com.example.sensitivedetection.ambiguity.model.AmbiguityResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 步骤2：确定性门控。对未命中缓存的字段计算 B000005 短路、hitB2、hitB4。
 */
@Slf4j
@Component
public class GateNode implements NodeAction {

    private final AmbiguityGate gate;

    public GateNode(AmbiguityGate gate) {
        this.gate = gate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<AmbiguityResult> results = (List<AmbiguityResult>) state.value("results").orElseThrow();

        int spare = 0;
        for (AmbiguityResult r : results) {
            if (r.isCacheHit()) {
                continue;
            }
            gate.apply(r);
            if (r.isSpare()) {
                spare++;
            }
        }
        log.info("步骤2: 门控完成，备用类字段(B000005) {} 条", spare);
        return Map.of("results", results);
    }
}
