package com.example.sensitivedetection.ambiguity.cache;

import com.example.sensitivedetection.ambiguity.model.AmbiguityResult;

import java.util.Collection;
import java.util.Map;

/**
 * 歧义检测结果缓存。实现可落库（JDBC）或禁用（NoOp）。
 * 为应对百万级数据，读写都按批进行。
 */
public interface AmbiguityCacheRepository {

    /** 批量按缓存 key 查询；返回命中的 key→记录。 */
    Map<String, CachedAmbiguity> loadByKeys(Collection<String> keys);

    /** 批量写入/更新（按 cache_key UPSERT）。 */
    void saveAll(Collection<AmbiguityResult> results);

    /** 是否启用（NoOp 返回 false，便于节点跳过）。 */
    boolean enabled();
}
