package com.example.sensitivedetection.security.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.security.config.SecurityClassificationProperties;
import com.example.sensitivedetection.security.model.SecurityClassificationResult;
import com.example.sensitivedetection.security.repository.SecuritySynonymRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 同义词替换/扩展（对应设计文档 8.3.2）。
 * replace 模式：仅用 target_term 检索；expand 模式：原词 + 同义词一起检索。
 */
@Slf4j
@Component
public class SynonymReplaceNode implements NodeAction {

    private final SecuritySynonymRepository synonymRepository;
    private final SecurityClassificationProperties props;

    public SynonymReplaceNode(SecuritySynonymRepository synonymRepository,
                              SecurityClassificationProperties props) {
        this.synonymRepository = synonymRepository;
        this.props = props;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        SecurityClassificationResult r = (SecurityClassificationResult) state.value("result").orElseThrow();
        if (r.isExempt()) {
            return Map.of("result", r);
        }

        String original = r.getColumnChnName();
        List<String> targets = synonymRepository.findTargetTerms(r.getSystemName(), original);

        List<String> queryTerms = new ArrayList<>();
        boolean expand = "expand".equalsIgnoreCase(props.getSynonym().getMode());
        if (targets.isEmpty()) {
            queryTerms.add(original);
        } else if (expand) {
            queryTerms.add(original);
            queryTerms.addAll(targets);
        } else {
            queryTerms.add(targets.get(0));
        }
        r.setQueryTerms(queryTerms);
        return Map.of("result", r);
    }
}
