-- 歧义检测结果缓存表（MySQL）—— 白名单语义
-- 只存"无歧义(通过)"的字段：命中=已知良好，直接跳过 LLM；未命中=新字段或曾被标记，需重判。
-- 因此 has_ambiguity 实际恒为 0、ambiguities_json 恒为 []，保留列仅为通用性。
-- key = md5(promptVersion + 表中英文名 + 字段中英文名)，不含缩写。
-- 百万级性能要点：
--   1) 主键 cache_key 为 md5 十六进制（32 位定长），等值/IN 查询走聚簇主键，无需回表扫描；
--   2) 读用 WHERE cache_key IN (...) 分批(1000)，写用 batchUpdate + ON DUPLICATE KEY UPDATE 分批(500)；
--   3) 白名单只增不更，且表大小受"去重后的良好字段数"约束，而非处理过的总行数；
--   4) prompt_version 用于换版本后清理旧缓存（key 已含版本，旧行只是冗余，可分批删）。
-- 进一步压缩索引：可把 cache_key 改为 BINARY(16) 存原始 md5 字节，索引减半（应用侧同步改）。

CREATE TABLE IF NOT EXISTS ambiguity_cache (
    cache_key           CHAR(32)     NOT NULL COMMENT 'md5(promptVersion+表中英文名+字段中英文名+缩写)',
    table_cn            VARCHAR(256)          DEFAULT NULL,
    table_en            VARCHAR(256)          DEFAULT NULL,
    field_cn            VARCHAR(256)          DEFAULT NULL,
    field_en            VARCHAR(256)          DEFAULT NULL,
    has_ambiguity       TINYINT(1)   NOT NULL DEFAULT 0,
    ambiguities_json    TEXT                  DEFAULT NULL COMMENT '命中规则列表 JSON',
    summary             VARCHAR(512)          DEFAULT NULL,
    disambiguation_path VARCHAR(512)          DEFAULT NULL,
    prompt_version      VARCHAR(32)  NOT NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (cache_key),
    KEY idx_prompt_version (prompt_version)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '语义歧义检测结果缓存';

-- 换 prompt 版本后清理旧缓存示例：
-- DELETE FROM ambiguity_cache WHERE prompt_version <> 'amb-v4' LIMIT 10000;  -- 分批删，避免大事务
