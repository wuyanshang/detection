package com.example.sensitivedetection.ambiguity.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.ambiguity.cache.AmbiguityCacheRepository;
import com.example.sensitivedetection.ambiguity.cache.CachedAmbiguity;
import com.example.sensitivedetection.ambiguity.model.AmbiguityResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 步骤1：批量加载缓存。命中的字段直接回填结论并短路（semanticDone=true）。
 */
@Slf4j
@Component
public class CacheLoadNode implements NodeAction {

    private final AmbiguityCacheRepository cacheRepository;

    public CacheLoadNode(AmbiguityCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<AmbiguityResult> results = (List<AmbiguityResult>) state.value("results").orElseThrow();

        if (!cacheRepository.enabled()) {
            log.info("步骤1: 缓存未启用，跳过");
            return Map.of("results", results);
        }

        List<String> keys = results.stream()
                .map(AmbiguityResult::getCacheKey)
                .distinct()
                .collect(Collectors.toList());
        Map<String, CachedAmbiguity> hits = cacheRepository.loadByKeys(keys);

        int hitCount = 0;
        for (AmbiguityResult r : results) {
            CachedAmbiguity c = hits.get(r.getCacheKey());
            if (c != null) {
                r.setHasAmbiguity(c.hasAmbiguity());
                r.setAmbiguities(c.ambiguities() == null ? new ArrayList<>() : c.ambiguities());
                r.setSummary(c.summary());
                r.setDisambiguationPath(c.disambiguationPath());
                r.setCacheHit(true);
                r.setSemanticDone(true);
                r.setSource("缓存");
                hitCount++;
            }
        }
        log.info("步骤1: 缓存命中 {} / {} 条", hitCount, results.size());
        return Map.of("results", results);
    }
}
