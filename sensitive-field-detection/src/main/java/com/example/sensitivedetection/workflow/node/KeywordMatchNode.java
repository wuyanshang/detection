package com.example.sensitivedetection.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.model.FieldResult;
import com.example.sensitivedetection.sensitive.KeywordMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 步骤2：关键词匹配（排除词 + 敏感词）
 */
@Slf4j
@Component
public class KeywordMatchNode implements NodeAction {

    private final KeywordMatcher keywordMatcher;

    public KeywordMatchNode(KeywordMatcher keywordMatcher) {
        this.keywordMatcher = keywordMatcher;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<FieldResult> results = (List<FieldResult>) state.value("results").orElseThrow();

        log.info("步骤2: 执行关键词匹配");
        for (FieldResult result : results) {
            keywordMatcher.match(result);
        }

        long kwSensitive = results.stream().filter(r -> "关键词".equals(r.getSensitiveSource())).count();
        long kwExcluded = results.stream().filter(r -> "关键词排除".equals(r.getSensitiveSource())).count();
        long kwUndecided = results.stream().filter(r -> r.getSensitiveSource() == null).count();
        log.info("  关键词匹配完成: 敏感 {} 条, 排除 {} 条, 未决 {} 条", kwSensitive, kwExcluded, kwUndecided);

        return Map.of("results", results, "hasUndecided", kwUndecided > 0);
    }
}
