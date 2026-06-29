package com.example.sensitivedetection.security.llm;

import com.example.sensitivedetection.security.config.SecurityClassificationProperties;
import com.example.sensitivedetection.security.model.CandidateItem;
import com.example.sensitivedetection.security.model.SecurityClassificationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 安全分类分级 AI 判定（对应设计文档 7）。
 * 复用现有 OpenAiChatModel（DashScope 兼容）；temperature=0 保证可复现。
 */
@Slf4j
@Component
public class SecurityMatchLlmClient {

    private final OpenAiChatModel chatModel;
    private final SecurityClassificationProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String template;

    private final OpenAiChatOptions deterministicOptions = OpenAiChatOptions.builder()
            .temperature(0.0)
            .build();

    public SecurityMatchLlmClient(OpenAiChatModel chatModel, SecurityClassificationProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        this.template = load("prompts/security-classification.txt");
        log.info("安全分类分级 prompt 加载完成");
    }

    /**
     * 从候选列表判定 category。返回 null 表示多次失败（调用方按未匹配处理）。
     */
    public SecurityMatchResponse classify(SecurityClassificationResult r) {
        String prompt = fill(r);
        String json = callWithRetry(prompt, r);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SecurityMatchResponse.class);
        } catch (Exception e) {
            log.warn("安全分级 JSON 解析失败 {}.{}: {}", r.getTableName(), r.getColumnName(), e.getMessage());
            return null;
        }
    }

    private String callWithRetry(String prompt, SecurityClassificationResult r) {
        int maxRetries = properties.getMaxRetries();
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String response = ChatClient.create(chatModel)
                        .prompt()
                        .user(prompt)
                        .options(deterministicOptions)
                        .call()
                        .content();
                return extractJson(response);
            } catch (Exception e) {
                log.warn("安全分级 LLM 调用失败 ({}/{}) {}.{}: {}", attempt, maxRetries,
                        r.getTableName(), r.getColumnName(), e.getMessage());
            }
        }
        return null;
    }

    private String fill(SecurityClassificationResult r) {
        return template
                .replace("{{systemName}}", nz(r.getSystemName()))
                .replace("{{systemDesc}}", nz(r.getSystemDesc()))
                .replace("{{tableName}}", nz(r.getTableName()))
                .replace("{{tableChnName}}", nz(r.getTableChnName()))
                .replace("{{columnName}}", nz(r.getColumnName()))
                .replace("{{columnChnName}}", nz(r.getColumnChnName()))
                .replace("{{candidateList}}", buildCandidateList(r.getCandidates()));
    }

    private String buildCandidateList(List<CandidateItem> candidates) {
        try {
            return objectMapper.writeValueAsString(candidates == null ? List.of() : candidates);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String load(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("加载 prompt 失败: " + path, e);
        }
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private String extractJson(String response) {
        if (response == null) {
            throw new RuntimeException("LLM 返回为空");
        }
        String t = response.trim();
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end >= start) {
            return t.substring(start, end + 1);
        }
        throw new RuntimeException("无法从响应中提取 JSON: " + t);
    }
}
