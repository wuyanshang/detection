package com.example.sensitivedetection.security.repository;

import com.example.sensitivedetection.security.model.SecurityClassificationResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * DB 未启用时的空实现：永不命中免检、不写库。
 * 当 security-classification.db.enabled 缺省或为 false 时生效。
 */
@Slf4j
@Repository
@ConditionalOnProperty(prefix = "security-classification.db", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class NoOpSecurityClassificationResultRepository implements SecurityClassificationResultRepository {

    @PostConstruct
    public void init() {
        log.info("安全分级结果缓存未启用（NoOp）：每次均走完整流程，不读写库");
    }

    @Override
    public Optional<CachedClassification> findByCacheKey(String cacheKey) {
        return Optional.empty();
    }

    @Override
    public void save(SecurityClassificationResult result) {
        // no-op
    }

    @Override
    public boolean enabled() {
        return false;
    }
}
