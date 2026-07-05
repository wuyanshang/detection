package com.example.sensitivedetection.classify2.es;

import com.example.sensitivedetection.classify2.config.ClassifyV2Properties;
import com.example.sensitivedetection.classify2.model.SearchHitV2;
import com.example.sensitivedetection.security.config.EsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v2 目录检索：查 safe_all_topic_v2，向量匹配 asset_embedding，
 * BM25 用 multi_match 覆盖 asset + content + example，_source 带出 content/定义/安全级别。
 * 复用 EsProperties 的连接信息（host/账号），索引名走 ClassifyV2Properties。
 */
@Slf4j
@Service
public class CatalogSearchV2Service {

    private final EsProperties esProps;
    private final ClassifyV2Properties v2Props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String authHeader;

    public CatalogSearchV2Service(EsProperties esProps, ClassifyV2Properties v2Props) {
        this.esProps = esProps;
        this.v2Props = v2Props;
        this.restClient = RestClient.builder().baseUrl(esProps.getHost()).build();
        if (esProps.getUsername() != null && !esProps.getUsername().isBlank()) {
            String token = esProps.getUsername() + ":" + (esProps.getPassword() == null ? "" : esProps.getPassword());
            this.authHeader = "Basic " + Base64.getEncoder()
                    .encodeToString(token.getBytes(StandardCharsets.UTF_8));
        } else {
            this.authHeader = null;
        }
    }

    /** 向量检索（kNN）。 */
    public List<SearchHitV2> vectorSearch(float[] queryVector) {
        ClassifyV2Properties.VectorSearch v = v2Props.getVectorSearch();

        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("field", "asset_embedding");
        knn.put("query_vector", toList(queryVector));
        knn.put("k", v.getTopK());
        knn.put("num_candidates", v.getNumCandidates());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("knn", knn);
        body.put("min_score", v.getMinScore());
        body.put("size", v.getTopK());
        body.put("_source", sourceFields());

        return search(body, "vector");
    }

    /** BM25：multi_match 覆盖 asset / content / example。 */
    public List<SearchHitV2> bm25Search(String queryText) {
        ClassifyV2Properties.Bm25Search b = v2Props.getBm25Search();

        Map<String, Object> multiMatch = new LinkedHashMap<>();
        multiMatch.put("query", queryText);
        multiMatch.put("fields", List.of("asset^2", "example^1.5", "content"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", Map.of("multi_match", multiMatch));
        body.put("size", b.getTopK());
        body.put("_source", sourceFields());

        return search(body, "bm25");
    }

    private List<String> sourceFields() {
        return List.of("asset", "category", "content", "level3_definition", "security_level", "regulatory_level");
    }

    private List<SearchHitV2> search(Map<String, Object> body, String source) {
        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri("/{index}/_search", v2Props.getIndex())
                    .contentType(MediaType.APPLICATION_JSON);
            if (authHeader != null) {
                spec = spec.header("Authorization", authHeader);
            }
            String resp = spec.body(body).retrieve().body(String.class);
            return parseHits(resp, source);
        } catch (Exception e) {
            throw new EsSearchException(source + " 检索失败: " + e.getMessage(), e);
        }
    }

    private List<SearchHitV2> parseHits(String resp, String source) throws Exception {
        List<SearchHitV2> out = new ArrayList<>();
        JsonNode hits = objectMapper.readTree(resp).path("hits").path("hits");
        if (hits.isArray()) {
            for (JsonNode hit : hits) {
                JsonNode src = hit.path("_source");
                Integer sec = src.has("security_level") && !src.path("security_level").isNull()
                        ? src.path("security_level").asInt() : null;
                String reg = src.has("regulatory_level") && !src.path("regulatory_level").isNull()
                        ? src.path("regulatory_level").asText() : null;
                out.add(new SearchHitV2(
                        src.path("category").asText(""),
                        src.path("asset").asText(""),
                        src.path("content").asText(""),
                        src.path("level3_definition").asText(""),
                        sec,
                        reg,
                        hit.path("_score").asDouble(0.0),
                        source));
            }
        }
        return out;
    }

    private List<Float> toList(float[] vec) {
        List<Float> list = new ArrayList<>(vec.length);
        for (float f : vec) {
            list.add(f);
        }
        return list;
    }

    public static class EsSearchException extends RuntimeException {
        public EsSearchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
