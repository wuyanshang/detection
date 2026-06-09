package com.example.sensitivedetection.ambiguity.cache;

import com.example.sensitivedetection.ambiguity.model.Ambiguity;

import java.util.List;

/**
 * 缓存命中后回放给结果的最小内容。
 */
public record CachedAmbiguity(
        boolean hasAmbiguity,
        List<Ambiguity> ambiguities,
        String summary,
        String disambiguationPath) {
}
