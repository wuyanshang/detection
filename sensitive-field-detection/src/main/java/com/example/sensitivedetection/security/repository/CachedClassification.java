package com.example.sensitivedetection.security.repository;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 从结果缓存表加载的精简记录，用于免检判定。
 */
@Data
@AllArgsConstructor
public class CachedClassification {

    private String category;        // 可能为 null（未匹配）
    private String matchedCatalog;
    private String reason;
    private String ruleVersion;
    private LocalDateTime expireAt;  // 可能为 null（永久有效）

    public boolean isNullResult() {
        return category == null || category.isBlank();
    }
}
