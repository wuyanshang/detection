package com.example.sensitivedetection.security.embedding;

import com.example.sensitivedetection.security.config.EmbeddingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通过 DashScope 的 OpenAI 兼容 /embeddings 接口生成向量。
 * 采用 RestClient + 原生 JSON，避免引入额外 SDK 依赖。
 * 带一个简单的 LRU 本地缓存（按文本），缓解重复字段的重复请求。
 */
@Slf4j
@Service
public class DashScopeEmbeddingService implements EmbeddingService {

    private final EmbeddingProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, float[]> cache;

    public DashScopeEmbeddingService(EmbeddingProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .build();
        int max = Math.max(16, props.getCache().getMaxSize());
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                return size() > max;
            }
        });
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("embedding 输入为空");
        }
        String key = text.trim();
        if (props.getCache().isEnabled()) {
            float[] cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getModel());
        body.put("input", key);
        body.put("dimensions", props.getDimension());

        try {
            String resp = restClient.post()
                    .uri("/embeddings")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            float[] vec = parseEmbedding(resp);
            if (props.getCache().isEnabled()) {
                cache.put(key, vec);
            }
            return vec;
        } catch (Exception e) {
            throw new EmbeddingException("生成 embedding 失败: " + e.getMessage(), e);
        }
    }

    private float[] parseEmbedding(String resp) throws Exception {
        JsonNode root = objectMapper.readTree(resp);
        JsonNode arr = root.path("data").path(0).path("embedding");
        if (!arr.isArray() || arr.isEmpty()) {
            throw new EmbeddingException("embedding 响应格式异常: " + resp);
        }
        float[] vec = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            vec[i] = (float) arr.get(i).asDouble();
        }
        return vec;
    }

    /** embedding 调用异常，供上层降级（仅 BM25）判断。 */
    public static class EmbeddingException extends RuntimeException {
        public EmbeddingException(String message, Throwable cause) {
            super(message, cause);
        }

        public EmbeddingException(String message) {
            super(message);
        }
    }
}
