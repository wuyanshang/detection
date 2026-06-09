package com.example.sensitivedetection.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.model.FieldResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 步骤4：汇总统计
 */
@Slf4j
@Component
public class SummarizeNode implements NodeAction {

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<FieldResult> results = (List<FieldResult>) state.value("results").orElseThrow();

        long intercepted = results.stream().filter(r -> "拦截".equals(r.getQualityResult())).count();
        long totalSensitive = results.stream().filter(r -> "是".equals(r.getIsSuspectedSensitive())).count();
        long totalNonSensitive = results.stream().filter(r -> "否".equals(r.getIsSuspectedSensitive())).count();
        long totalUncertain = results.stream().filter(r -> "不确定".equals(r.getIsSuspectedSensitive())).count();
        long totalPending = results.stream().filter(r -> "待确认".equals(r.getIsSuspectedSensitive())).count();

        log.info("========== 检测完成 ==========");
        log.info("  总计: {} 条", results.size());
        log.info("  质检拦截: {} 条", intercepted);
        log.info("  疑似敏感: {} 条, 非敏感: {} 条, 不确定: {} 条, 待确认(LLM失败): {} 条",
                totalSensitive, totalNonSensitive, totalUncertain, totalPending);

        return Map.of("results", results);
    }
}
