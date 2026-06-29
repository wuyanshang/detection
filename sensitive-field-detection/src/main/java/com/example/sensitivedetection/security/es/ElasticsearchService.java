package com.example.sensitivedetection.security.es;

import com.example.sensitivedetection.security.config.EsProperties;
import com.example.sensitivedetection.security.config.SecurityClassificationProperties;
import com.example.sensitivedetection.security.model.SearchResult;
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
 * Elasticsearch 检索服务（对应设计文档 5.3）。
 * 采用 RestClient 直接发起 _search（原生 JSON），版本容忍、无需额外客户端 SDK。
 *
 * 索引字段约定：asset(text) / category(keyword) / asset_embedding(dense_vector 1024)。
 */
@Slf4j
@Service
public class ElasticsearchService {

    private final EsProperties esProps;
    private final SecurityClassificationProperties scProps;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String authHeader;

    public ElasticsearchService(EsProperties esProps, SecurityClassificationProperties scProps) {
        this.esProps = esProps;
        this.scProps = scProps;
        this.restClient = RestClient.builder()
                .baseUrl(esProps.getHost())
                .build();
        if (esProps.getUsername() != null && !esProps.getUsername().isBlank()) {
            String token = esProps.getUsername() + ":" + (esProps.getPassword() == null ? "" : esProps.getPassword());
            this.authHeader = "Basic " + Base64.getEncoder()
                    .encodeToString(token.getBytes(StandardCharsets.UTF_8));
        } else {
            this.authHeader = null;
        }
    }

    /**
     * 向量检索（kNN）。阈值用顶层 min_score（非 knn.filter），见文档 5.3.1。
     */
    public List<SearchResult> vectorSearch(float[] queryVector) {
        SecurityClassificationProperties.VectorSearch v = scProps.getVectorSearch();

        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("field", "asset_embedding");
        knn.put("query_vector", toList(queryVector));
        knn.put("k", v.getTopK());
        knn.put("num_candidates", v.getNumCandidates());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("knn", knn);
        body.put("min_score", v.getMinScore());
        body.put("size", v.getTopK());
        body.put("_source", List.of("asset", "category", "topic"));

        return search(body, "vector");
    }

    /**
     * BM25 关键词检索，匹配 asset 字段。
     */
    public List<SearchResult> bm25Search(String queryText) {
        SecurityClassificationProperties.Bm25Search b = scProps.getBm25Search();

        Map<String, Object> match = Map.of("asset", Map.of("query", queryText));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", Map.of("match", match));
        body.put("size", b.getTopK());
        body.put("_source", List.of("asset", "category", "topic"));

        return search(body, "bm25");
    }

    private List<SearchResult> search(Map<String, Object> body, String source) {
        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri("/{index}/_search", esProps.getIndex())
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

    private List<SearchResult> parseHits(String resp, String source) throws Exception {
        List<SearchResult> out = new ArrayList<>();
        JsonNode hits = objectMapper.readTree(resp).path("hits").path("hits");
        if (hits.isArray()) {
            for (JsonNode hit : hits) {
                JsonNode src = hit.path("_source");
                SearchResult r = new SearchResult(
                        src.path("category").asText(""),
                        src.path("asset").asText(""),
                        hit.path("_score").asDouble(0.0),
                        source);
                out.add(r);
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

    /** ES 检索异常，供上层降级判断。 */
    public static class EsSearchException extends RuntimeException {
        public EsSearchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
