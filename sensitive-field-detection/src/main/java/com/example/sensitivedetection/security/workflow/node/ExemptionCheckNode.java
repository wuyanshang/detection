package com.example.sensitivedetection.security.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.security.config.SecurityClassificationProperties;
import com.example.sensitivedetection.security.model.SecurityClassificationResult;
import com.example.sensitivedetection.security.repository.CachedClassification;
import com.example.sensitivedetection.security.repository.SecurityClassificationResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 免检判定（对应设计文档 8.3.1）。
 * 命中且有效的缓存 → 标记 exempt 并回填结果，后续节点短路。
 */
@Slf4j
@Component
public class ExemptionCheckNode implements NodeAction {

    private final SecurityClassificationResultRepository repository;
    private final SecurityClassificationProperties props;

    public ExemptionCheckNode(SecurityClassificationResultRepository repository,
                              SecurityClassificationProperties props) {
        this.repository = repository;
        this.props = props;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        SecurityClassificationResult r = (SecurityClassificationResult) state.value("result").orElseThrow();

        if (r.isForceRefresh() || !repository.enabled()) {
            return Map.of("result", r);
        }

        Optional<CachedClassification> cachedOpt = repository.findByCacheKey(r.getCacheKey());
        if (cachedOpt.isEmpty()) {
            return Map.of("result", r);
        }
        CachedClassification cached = cachedOpt.get();

        if (!isValid(cached)) {
            log.debug("缓存失效，重算: {}", r.getCacheKey());
            return Map.of("result", r);
        }

        r.setExempt(true);
        r.setCategory(cached.getCategory());
        r.setMatchedCatalog(cached.getMatchedCatalog());
        r.setReason(cached.getReason());
        r.setSource("cache");
        return Map.of("result", r);
    }

    private boolean isValid(CachedClassification cached) {
        // rule_version 不一致 → 失效
        if (!props.getCache().getRuleVersion().equals(cached.getRuleVersion())) {
            return false;
        }
        // 过期 → 失效
        if (cached.getExpireAt() != null && cached.getExpireAt().isBefore(LocalDateTime.now())) {
            return false;
        }
        // null 结果默认不参与免检（除非配置允许）
        if (cached.isNullResult() && !props.getCache().isCacheNullResult()) {
            return false;
        }
        return true;
    }
}
