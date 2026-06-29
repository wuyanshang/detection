package com.example.sensitivedetection.security.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 同义词规则落库实现（MySQL）。表 dq_security_synonym_rule。
 * 支持 EXACT 全等与 CONTAINS 包含匹配；系统级规则优先于全局规则，再按 priority 降序。
 */
@Slf4j
@Repository
@ConditionalOnProperty(prefix = "security-classification.db", name = "enabled", havingValue = "true")
public class JdbcSecuritySynonymRepository implements SecuritySynonymRepository {

    private final JdbcTemplate jdbc;

    public JdbcSecuritySynonymRepository(JdbcTemplate securityJdbcTemplate) {
        this.jdbc = securityJdbcTemplate;
        log.info("安全分级同义词规则已启用（JDBC）");
    }

    @Override
    public List<String> findTargetTerms(String systemName, String columnChnName) {
        if (columnChnName == null || columnChnName.isBlank()) {
            return new ArrayList<>();
        }
        // 系统级规则在前（system_name 命中排前），再按 priority 降序、EXACT 优先。
        String sql = "SELECT target_term FROM dq_security_synonym_rule "
                + "WHERE is_active = 1 AND (system_name = ? OR system_name IS NULL) "
                + "AND ( (match_type = 'EXACT' AND source_term = ?) "
                + "   OR (match_type = 'CONTAINS' AND ? LIKE CONCAT('%', source_term, '%')) ) "
                + "ORDER BY (system_name = ?) DESC, priority DESC, "
                + " CASE match_type WHEN 'EXACT' THEN 0 WHEN 'CONTAINS' THEN 1 ELSE 2 END";
        return jdbc.query(sql,
                (rs, n) -> rs.getString("target_term"),
                systemName, columnChnName, columnChnName, systemName);
    }

    @Override
    public boolean enabled() {
        return true;
    }
}
