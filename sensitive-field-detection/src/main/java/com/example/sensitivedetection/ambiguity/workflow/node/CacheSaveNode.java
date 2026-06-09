package com.example.sensitivedetection.ambiguity.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.sensitivedetection.ambiguity.cache.AmbiguityCacheRepository;
import com.example.sensitivedetection.ambiguity.model.AmbiguityResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 步骤6：把本次新算出的结果批量落库（命中缓存的不重复写）。
 */
@Slf4j
@Component
public class CacheSaveNode implements NodeAction {

    private final AmbiguityCacheRepository cacheRepository;

    public CacheSaveNode(AmbiguityCacheRepository cacheRepository) {
        this.cacheRepository = cacheRepository;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(OverAllState state) {
        List<AmbiguityResult> results = (List<AmbiguityResult>) state.value("results").orElseThrow();

        if (!cacheRepository.enabled()) {
            return Map.of("results", results);
        }
        // 白名单策略：只把"本次新算出且无歧义(通过)"的字段落库。
        // 有歧义的不缓存——它们是待修复项，修复后名字会变(key 变)、或需重判，缓存反而会读到过期结论。
        List<AmbiguityResult> toSave = results.stream()
                .filter(r -> !r.isCacheHit() && !r.isHasAmbiguity())
                .collect(Collectors.toList());
        cacheRepository.saveAll(toSave);
        log.info("步骤6: 落库(白名单/无歧义) {} 条", toSave.size());
        return Map.of("results", results);
    }
}
