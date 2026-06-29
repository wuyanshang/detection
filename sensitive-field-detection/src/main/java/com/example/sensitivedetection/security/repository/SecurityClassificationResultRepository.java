package com.example.sensitivedetection.security.repository;

import com.example.sensitivedetection.security.model.SecurityClassificationResult;

import java.util.Optional;

/**
 * 安全分级结果表访问（兼作免检缓存，对应设计文档 4.1）。
 * 实现可落库（JDBC）或禁用（NoOp）。
 */
public interface SecurityClassificationResultRepository {

    /** 按 cacheKey 查询缓存记录。 */
    Optional<CachedClassification> findByCacheKey(String cacheKey);

    /** UPSERT 写入/更新一条分级结果。 */
    void save(SecurityClassificationResult result);

    /** 是否启用（NoOp 返回 false）。 */
    boolean enabled();
}
