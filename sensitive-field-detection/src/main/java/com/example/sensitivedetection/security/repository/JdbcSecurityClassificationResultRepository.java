package com.example.sensitivedetection.security.repository;

import com.example.sensitivedetection.security.config.SecurityClassificationProperties;
import com.example.sensitivedetection.security.model.SecurityClassificationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 落库实现（MySQL）。表结构见 resources/db/security-classification-ddl.sql。
 * UPSERT 为 MySQL 方言（INSERT ... ON DUPLICATE KEY UPDATE）。
 */
@Slf4j
@Repository
@ConditionalOnProperty(prefix = "security-classification.db", name = "enabled", havingValue = "true")
public class JdbcSecurityClassificationResultRepository implements SecurityClassificationResultRepository {

    private final JdbcTemplate jdbc;
    private final SecurityClassificationProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcSecurityClassificationResultRepository(JdbcTemplate securityJdbcTemplate,
                                                      SecurityClassificationProperties props) {
        this.jdbc = securityJdbcTemplate;
        this.props = props;
        log.info("安全分级结果缓存已启用（JDBC 落库）");
    }

    @Override
    public Optional<CachedClassification> findByCacheKey(String cacheKey) {
        String sql = "SELECT category, matched_catalog, ai_reason, rule_version, expire_at "
                + "FROM dq_security_classification_result WHERE cache_key = ? LIMIT 1";
        List<CachedClassification> list = jdbc.query(sql, (rs, n) -> new CachedClassification(
                rs.getString("category"),
                rs.getString("matched_catalog"),
                rs.getString("ai_reason"),
                rs.getString("rule_version"),
                toLdt(rs.getTimestamp("expire_at"))), cacheKey);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public void save(SecurityClassificationResult r) {
        LocalDateTime expireAt = props.getCache().getTtlHours() > 0
                ? LocalDateTime.now().plusHours(props.getCache().getTtlHours())
                : null;

        String sql = "INSERT INTO dq_security_classification_result "
                + "(cache_key, system_name, system_desc, table_name, table_chn_name, column_name, column_chn_name, "
                + " category, matched_catalog, is_exempt, rule_version, expire_at, "
                + " vector_search_results, bm25_search_results, ai_reason, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW()) "
                + "ON DUPLICATE KEY UPDATE system_desc=VALUES(system_desc), table_chn_name=VALUES(table_chn_name), "
                + " column_chn_name=VALUES(column_chn_name), category=VALUES(category), "
                + " matched_catalog=VALUES(matched_catalog), rule_version=VALUES(rule_version), "
                + " expire_at=VALUES(expire_at), vector_search_results=VALUES(vector_search_results), "
                + " bm25_search_results=VALUES(bm25_search_results), ai_reason=VALUES(ai_reason), updated_at=NOW()";

        jdbc.update(sql,
                r.getCacheKey(),
                r.getSystemName(),
                r.getSystemDesc(),
                r.getTableName(),
                r.getTableChnName(),
                r.getColumnName(),
                r.getColumnChnName(),
                r.getCategory(),
                r.getMatchedCatalog(),
                0,
                props.getCache().getRuleVersion(),
                expireAt == null ? null : Timestamp.valueOf(expireAt),
                toJson(r.getVectorResults()),
                toJson(r.getBm25Results()),
                r.getReason());
    }

    @Override
    public boolean enabled() {
        return true;
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o == null ? List.of() : o);
        } catch (Exception e) {
            return "[]";
        }
    }

    private LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
