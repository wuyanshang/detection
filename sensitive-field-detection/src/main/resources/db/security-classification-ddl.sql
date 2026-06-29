-- 安全分类分级 DDL（MySQL）。对应设计文档第 4 节。
-- 启用方式：application.yml 配置 security-classification.db.enabled=true 且配置 datasource。

-- ============================================================
-- 1) 安全分级结果表（兼作免检缓存）
--    免检：cache_key 命中且有效（rule_version 一致、未过期、非未参与免检的 null 结果）即直接返回。
-- ============================================================
CREATE TABLE IF NOT EXISTS dq_security_classification_result (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    cache_key             VARCHAR(500) NOT NULL COMMENT '缓存key：systemName|tableName|columnName',
    system_name           VARCHAR(100) NOT NULL COMMENT '系统名',
    system_desc           VARCHAR(500)          DEFAULT NULL COMMENT '系统描述',
    table_name            VARCHAR(100) NOT NULL COMMENT '表英文名',
    table_chn_name        VARCHAR(200)          DEFAULT NULL COMMENT '表中文名',
    column_name           VARCHAR(100) NOT NULL COMMENT '字段英文名',
    column_chn_name       VARCHAR(200)          DEFAULT NULL COMMENT '字段中文名',

    -- 分级结果
    category              VARCHAR(500)          DEFAULT NULL COMMENT '数据分类（来自关键项目录）',
    matched_catalog       VARCHAR(200)          DEFAULT NULL COMMENT '命中的关键项目录名称（asset字段）',
    is_exempt             TINYINT      NOT NULL DEFAULT 0 COMMENT '是否免检',

    -- 缓存版本控制
    rule_version          VARCHAR(50)           DEFAULT NULL COMMENT 'ES知识库/规则版本，更新后用于判定缓存是否过期',
    expire_at             DATETIME              DEFAULT NULL COMMENT '缓存过期时间，NULL表示永久有效',

    -- 召回信息（调试/审计）
    vector_search_results JSON                  DEFAULT NULL COMMENT '向量检索结果列表',
    bm25_search_results   JSON                  DEFAULT NULL COMMENT 'BM25检索结果列表',
    ai_reason             TEXT                  DEFAULT NULL COMMENT 'AI判定理由',

    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_cache_key (cache_key),
    KEY idx_system (system_name),
    KEY idx_category (category(255))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '安全分级结果表';

-- ============================================================
-- 2) 同义词替换规则表
-- ============================================================
CREATE TABLE IF NOT EXISTS dq_security_synonym_rule (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    system_name  VARCHAR(100)          DEFAULT NULL COMMENT '系统名，NULL表示全局规则',
    source_term  VARCHAR(200) NOT NULL COMMENT '源词（如"自负金额"）',
    target_term  VARCHAR(200) NOT NULL COMMENT '目标词（如"免赔额"）',
    match_type   VARCHAR(20)  NOT NULL DEFAULT 'EXACT' COMMENT '匹配方式：EXACT全等/CONTAINS包含/REGEX正则',
    priority     INT          NOT NULL DEFAULT 0 COMMENT '优先级，越大越优先',
    is_active    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    KEY idx_system_source (system_name, source_term)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '安全分级同义词替换规则表';
