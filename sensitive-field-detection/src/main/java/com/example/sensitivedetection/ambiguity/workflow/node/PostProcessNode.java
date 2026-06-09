package com.example.sensitivedetection.ambiguity.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.ambiguity.AmbiguityConstants;
import com.example.sensitivedetection.ambiguity.model.Ambiguity;
import com.example.sensitivedetection.ambiguity.model.AmbiguityResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 步骤5：规则组合治理（确定性后处理，不影响一致性）。
 * 规则：
 *  - 理解类(B000001/B000003)触发 → 抑制规范类(B000002/B000004)；
 *  - 理解类内部冗余 → 只报一条，优先级 B000003 > B000001。
 * 即：一条数据要么报理解类 1 条，要么报规范类 1~2 条。
 */
@Slf4j
@Component
public class PostProcessNode implements NodeAction {

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<AmbiguityResult> results = (List<AmbiguityResult>) state.value("results").orElseThrow();

        int adjusted = 0;
        for (AmbiguityResult r : results) {
            // 缓存命中与备用短路的结论已最终化，不再治理
            if (r.isCacheHit() || r.isSpare()) {
                continue;
            }
            if (r.getAmbiguities().isEmpty()) {
                continue;
            }
            if (governance(r)) {
                adjusted++;
            }
        }
        log.info("步骤5: 规则组合治理，调整 {} 条", adjusted);
        return Map.of("results", results);
    }

    private boolean governance(AmbiguityResult r) {
        boolean hasB1 = r.hasRule(AmbiguityConstants.B000001);
        boolean hasB3 = r.hasRule(AmbiguityConstants.B000003);
        boolean understandingFired = hasB1 || hasB3;
        if (!understandingFired) {
            return false; // 仅规范类，可共报，无需治理
        }

        List<Ambiguity> kept = new ArrayList<>();
        // 理解类只保留一条：B000003 优先
        if (hasB3) {
            kept.add(find(r, AmbiguityConstants.B000003));
        } else {
            kept.add(find(r, AmbiguityConstants.B000001));
        }
        // 规范类被抑制（不加入）
        boolean changed = kept.size() != r.getAmbiguities().size();
        r.setAmbiguities(kept);
        r.setHasAmbiguity(true);
        return changed;
    }

    private Ambiguity find(AmbiguityResult r, String code) {
        return r.getAmbiguities().stream()
                .filter(a -> code.equals(a.getRuleCode()))
                .findFirst()
                .orElse(null);
    }
}
