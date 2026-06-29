package com.example.sensitivedetection.security.repository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * DB 未启用时的空实现：不替换，直接用原始字段中文名检索。
 */
@Slf4j
@Repository
@ConditionalOnProperty(prefix = "security-classification.db", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class NoOpSecuritySynonymRepository implements SecuritySynonymRepository {

    @PostConstruct
    public void init() {
        log.info("安全分级同义词规则未启用（NoOp）：不做同义词替换");
    }

    @Override
    public List<String> findTargetTerms(String systemName, String columnChnName) {
        return Collections.emptyList();
    }

    @Override
    public boolean enabled() {
        return false;
    }
}
