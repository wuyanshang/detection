package com.example.sensitivedetection.ambiguity.cache;

import com.example.sensitivedetection.ambiguity.model.AmbiguityResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * 缓存未启用时的空实现：始终未命中、不写库。
 * 当 ambiguity.cache.enabled 缺省或为 false 时生效。
 */
@Slf4j
@Repository
@ConditionalOnProperty(prefix = "ambiguity.cache", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpAmbiguityCacheRepository implements AmbiguityCacheRepository {

    @PostConstruct
    public void init() {
        log.info("歧义缓存未启用（NoOp）：每次均调用 LLM，不读写库");
    }

    @Override
    public Map<String, CachedAmbiguity> loadByKeys(Collection<String> keys) {
        return Collections.emptyMap();
    }

    @Override
    public void saveAll(Collection<AmbiguityResult> results) {
        // no-op
    }

    @Override
    public boolean enabled() {
        return false;
    }
}
