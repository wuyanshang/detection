package com.example.sensitivedetection.ambiguity.llm;

import com.example.sensitivedetection.ambiguity.AmbiguityConstants;
import com.example.sensitivedetection.ambiguity.model.AmbiguityResult;
import com.example.sensitivedetection.config.DetectionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 歧义检测的两阶段 LLM 调用封装。
 * 关键：temperature=0（贪心解码），尽量保证同输入结果稳定（跨请求一致仍依赖缓存落库）。
 */
@Slf4j
@Component
public class AmbiguityLlmClient {

    private final OpenAiChatModel chatModel;
    private final DetectionProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String stage1Template;
    private String stage2BaseTemplate;
    /** 规则码 → 判定标尺片段（resources/prompts/rules/{code}.txt） */
    private final Map<String, String> ruleFragments = new LinkedHashMap<>();

    // 温度 0（贪心解码），最大化可复现性。
    // 若使用 OpenAI 并想进一步增强可复现性，可加 .seed(42)（部分模型/版本支持）。
    private final OpenAiChatOptions deterministicOptions = OpenAiChatOptions.builder()
            .temperature(0.0)
            .build();

    public AmbiguityLlmClient(OpenAiChatModel chatModel, DetectionProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        this.stage1Template = load("prompts/ambiguity-stage1.txt");
        this.stage2BaseTemplate = load("prompts/ambiguity-stage2.txt");
        for (String code : AmbiguityConstants.STAGE2_RULES) {
            ruleFragments.put(code, load("prompts/rules/" + code + ".txt"));
        }
        log.info("歧义检测 prompt 加载完成（stage1 + stage2 基底 + {} 条规则片段）", ruleFragments.size());
    }

    /** 阶段一：消歧。返回 null 表示多次失败（调用方按消歧失败兜底）。 */
    public Stage1Response disambiguate(AmbiguityResult r) {
        String prompt = fill(stage1Template, r, null);
        String json = callWithRetry(prompt, r, "stage1");
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Stage1Response.class);
        } catch (Exception e) {
            log.warn("stage1 JSON 解析失败 {}.{}: {}", r.getTableEn(), r.getFieldEn(), e.getMessage());
            return null;
        }
    }

    /** 阶段二：按 rulesToCheck 动态拼接规则片段后分类（只注入命中规则，LLM 看不到其他规则）。 */
    public Stage2Response classify(AmbiguityResult r, Set<String> rulesToCheck) {
        String prompt = fill(stage2BaseTemplate, r, rulesToCheck)
                .replace("{rules_block}", buildRulesBlock(rulesToCheck));
        String json = callWithRetry(prompt, r, "stage2");
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Stage2Response.class);
        } catch (Exception e) {
            log.warn("stage2 JSON 解析失败 {}.{}: {}", r.getTableEn(), r.getFieldEn(), e.getMessage());
            return null;
        }
    }

    private String callWithRetry(String prompt, AmbiguityResult r, String stage) {
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
                log.warn("{} 调用失败 ({}/{}) {}.{}: {}", stage, attempt, maxRetries,
                        r.getTableEn(), r.getFieldEn(), e.getMessage());
            }
        }
        return null;
    }

    /** 按固定顺序拼接命中规则的片段；未命中的规则片段不出现在 prompt 中。 */
    private String buildRulesBlock(Set<String> rulesToCheck) {
        StringBuilder sb = new StringBuilder();
        for (String code : AmbiguityConstants.STAGE2_RULES) {
            if (rulesToCheck.contains(code)) {
                String fragment = ruleFragments.get(code);
                if (fragment != null && !fragment.isBlank()) {
                    sb.append(fragment.trim()).append("\n\n");
                }
            }
        }
        return sb.toString().trim();
    }

    private String fill(String template, AmbiguityResult r, Set<String> rulesToCheck) {
        String filled = template
                .replace("{table_cn}", nz(r.getTableCn()))
                .replace("{table_en}", nz(r.getTableEn()))
                .replace("{field_cn}", nz(r.getFieldCn()))
                .replace("{field_en}", nz(r.getFieldEn()))
                .replace("{abbreviation_mappings}", nz(r.getAbbreviationMappings()));
        if (rulesToCheck != null) {
            filled = filled.replace("{rules_to_check}", rulesToCheck.toString());
        }
        return filled;
    }

    private String load(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("加载 prompt 失败: " + path, e);
        }
    }

    private String nz(String s) {
        return s == null || s.isBlank() ? "" : s;
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
