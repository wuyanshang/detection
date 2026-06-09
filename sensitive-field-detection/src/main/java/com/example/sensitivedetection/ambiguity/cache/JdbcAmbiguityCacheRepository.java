package com.example.sensitivedetection.ambiguity.cache;

import com.example.sensitivedetection.ambiguity.AmbiguityConstants;
import com.example.sensitivedetection.ambiguity.model.Ambiguity;
import com.example.sensitivedetection.ambiguity.model.AmbiguityResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 落库缓存（MySQL）。百万级数据下的性能要点：
 *  - 主键 = cache_key，按批 IN 查询（每批 {@link #LOAD_CHUNK} 个），避免逐行往返；
 *  - 写入用 batchUpdate + ON DUPLICATE KEY UPDATE，按批 {@link #SAVE_CHUNK} 提交；
 *  - 只缓存"本次新算出"的结果（命中缓存的不重复写）。
 *
 * 启用方式：application.yml 配置 ambiguity.cache.enabled=true 且配置 spring.datasource。
 * 注意：UPSERT 语句为 MySQL 方言；PostgreSQL 请改为 INSERT ... ON CONFLICT (cache_key) DO UPDATE。
 */
@Slf4j
@Repository
@ConditionalOnProperty(prefix = "ambiguity.cache", name = "enabled", havingValue = "true")
public class JdbcAmbiguityCacheRepository implements AmbiguityCacheRepository {

    private static final int LOAD_CHUNK = 1000;
    private static final int SAVE_CHUNK = 500;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcAmbiguityCacheRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        log.info("歧义缓存已启用（JDBC 落库）");
    }

    @Override
    public Map<String, CachedAmbiguity> loadByKeys(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> keyList = new ArrayList<>(keys);
        Map<String, CachedAmbiguity> result = new HashMap<>(keyList.size() * 2);

        for (int i = 0; i < keyList.size(); i += LOAD_CHUNK) {
            List<String> chunk = keyList.subList(i, Math.min(i + LOAD_CHUNK, keyList.size()));
            String placeholders = String.join(",", Collections.nCopies(chunk.size(), "?"));
            String sql = "SELECT cache_key, has_ambiguity, ambiguities_json, summary, disambiguation_path "
                    + "FROM ambiguity_cache WHERE cache_key IN (" + placeholders + ")";
            jdbc.query(sql, chunk.toArray(), rs -> {
                result.put(rs.getString("cache_key"), new CachedAmbiguity(
                        rs.getBoolean("has_ambiguity"),
                        parseAmbiguities(rs.getString("ambiguities_json")),
                        rs.getString("summary"),
                        rs.getString("disambiguation_path")));
            });
        }
        return result;
    }

    @Override
    public void saveAll(Collection<AmbiguityResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        List<AmbiguityResult> toSave = new ArrayList<>(results);
        String sql = "INSERT INTO ambiguity_cache "
                + "(cache_key, table_cn, table_en, field_cn, field_en, has_ambiguity, ambiguities_json, "
                + " summary, disambiguation_path, prompt_version, updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,NOW()) "
                + "ON DUPLICATE KEY UPDATE has_ambiguity=VALUES(has_ambiguity), "
                + " ambiguities_json=VALUES(ambiguities_json), summary=VALUES(summary), "
                + " disambiguation_path=VALUES(disambiguation_path), prompt_version=VALUES(prompt_version), "
                + " updated_at=NOW()";

        for (int i = 0; i < toSave.size(); i += SAVE_CHUNK) {
            List<AmbiguityResult> chunk = toSave.subList(i, Math.min(i + SAVE_CHUNK, toSave.size()));
            jdbc.batchUpdate(sql, chunk, chunk.size(), (ps, r) -> {
                ps.setString(1, r.getCacheKey());
                ps.setString(2, r.getTableCn());
                ps.setString(3, r.getTableEn());
                ps.setString(4, r.getFieldCn());
                ps.setString(5, r.getFieldEn());
                ps.setBoolean(6, r.isHasAmbiguity());
                ps.setString(7, writeAmbiguities(r.getAmbiguities()));
                ps.setString(8, r.getSummary());
                ps.setString(9, r.getDisambiguationPath());
                ps.setString(10, AmbiguityConstants.PROMPT_VERSION);
            });
        }
        log.info("歧义缓存写入 {} 条", toSave.size());
    }

    @Override
    public boolean enabled() {
        return true;
    }

    private List<Ambiguity> parseAmbiguities(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Ambiguity>>() {
            });
        } catch (Exception e) {
            log.warn("解析缓存 ambiguities_json 失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String writeAmbiguities(List<Ambiguity> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? new ArrayList<>() : list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
