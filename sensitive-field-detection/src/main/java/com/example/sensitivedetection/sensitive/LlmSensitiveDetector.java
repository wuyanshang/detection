package com.example.sensitivedetection.sensitive;

import com.example.sensitivedetection.config.DetectionProperties;
import com.example.sensitivedetection.model.FieldResult;
import com.example.sensitivedetection.model.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class LlmSensitiveDetector {

    private final OpenAiChatModel chatModel;
    private final DetectionProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String promptTemplate;

    public LlmSensitiveDetector(OpenAiChatModel chatModel, DetectionProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/sensitive-detection.txt");
            promptTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("LLM prompt 模板加载成功");
        } catch (Exception e) {
            throw new RuntimeException("加载 prompt 模板失败", e);
        }
    }

    /**
     * 对单个字段进行 LLM 敏感识别
     */
    public void detect(FieldResult result) {
        String prompt = buildPrompt(result);
        int maxRetries = properties.getMaxRetries();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String response = ChatClient.create(chatModel)
                        .prompt()
                        .user(prompt)
                        .call()
                        .content();

                LlmResponse llmResponse = parseResponse(response);
                applyResult(result, llmResponse);
                return;
            } catch (Exception e) {
                log.warn("LLM 调用失败 (第 {}/{} 次), 字段: {}.{}, 错误: {}",
                        attempt, maxRetries, result.getTableEn(), result.getFieldEn(), e.getMessage());
                if (attempt == maxRetries) {
                    applyFallback(result);
                }
            }
        }
    }

    private String buildPrompt(FieldResult result) {
        return promptTemplate
                .replace("{system_cn}", safeStr(result.getSystemCn()))
                .replace("{table_cn}", safeStr(result.getTableCn()))
                .replace("{table_en}", safeStr(result.getTableEn()))
                .replace("{field_cn}", safeStr(result.getFieldCn()))
                .replace("{field_en}", safeStr(result.getFieldEn()));
    }

    private LlmResponse parseResponse(String response) throws Exception {
        // 提取 JSON 内容（LLM 可能会在 JSON 前后输出多余内容）
        String json = extractJson(response);
        return objectMapper.readValue(json, LlmResponse.class);
    }

    private String extractJson(String response) {
        if (response == null) {
            throw new RuntimeException("LLM 返回为空");
        }
        String trimmed = response.trim();
        // 尝试直接解析
        if (trimmed.startsWith("{")) {
            int end = trimmed.lastIndexOf("}");
            if (end >= 0) {
                return trimmed.substring(0, end + 1);
            }
        }
        // 尝试从 ```json ... ``` 中提取
        int jsonStart = trimmed.indexOf("```json");
        if (jsonStart >= 0) {
            int start = trimmed.indexOf("{", jsonStart);
            int end = trimmed.lastIndexOf("}");
            if (start >= 0 && end >= start) {
                return trimmed.substring(start, end + 1);
            }
        }
        // 尝试找第一个 { 和最后一个 }
        int start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");
        if (start >= 0 && end >= start) {
            return trimmed.substring(start, end + 1);
        }
        throw new RuntimeException("无法从 LLM 响应中提取 JSON: " + trimmed);
    }

    private void applyResult(FieldResult result, LlmResponse llmResponse) {
        if (llmResponse.isSuspectedSensitive()) {
            result.setIsSuspectedSensitive("是");
            result.setCatalogNode(llmResponse.getCatalogNode());
        } else {
            result.setIsSuspectedSensitive("否");
        }
        result.setSensitiveReason(llmResponse.getReason());
        result.setSensitiveSource("LLM");
    }

    /**
     * LLM 调用失败兜底：标记为待确认，由人工复核
     */
    private void applyFallback(FieldResult result) {
        log.error("LLM 重试 {} 次均失败, 字段 {}.{} 标记为待确认",
                properties.getMaxRetries(), result.getTableEn(), result.getFieldEn());
        result.setIsSuspectedSensitive("待确认");
        result.setCatalogNode(null);
        result.setSensitiveReason("LLM调用失败，需人工确认");
        result.setSensitiveSource("LLM失败");
    }

    private String safeStr(String s) {
        return s == null ? "" : s;
    }
}
