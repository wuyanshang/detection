package com.example.sensitivedetection.classify2.llm;

import com.example.sensitivedetection.classify2.config.ClassifyV2Properties;
import com.example.sensitivedetection.classify2.model.CandidateV2;
import com.example.sensitivedetection.classify2.model.FieldContext;
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
 * 字段判定：候选携带完整路径 + content + 三级定义，结合表级推断主体做一致性约束。
 * 失败返回 null（调用方按未匹配处理）。
 */
@Slf4j
@Component
public class FieldJudgeClient {

    private final OpenAiChatModel chatModel;
    private final ClassifyV2Properties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String template;

    private final OpenAiChatOptions deterministicOptions = OpenAiChatOptions.builder()
            .temperature(0.0)
            .build();

    public FieldJudgeClient(OpenAiChatModel chatModel, ClassifyV2Properties props) {
        this.chatModel = chatModel;
        this.props = props;
    }

    @PostConstruct
    public void init() {
        this.template = load("prompts/classify2-field-judge.txt");
        log.info("classify-v2 字段判定 prompt 加载完成");
    }

    public FieldJudgeResponse judge(FieldContext ctx) {
        String prompt = fill(ctx);
        for (int attempt = 1; attempt <= props.getMaxRetries(); attempt++) {
            try {
                String response = ChatClient.create(chatModel)
                        .prompt().user(prompt)
                        .options(deterministicOptions)
                        .call().content();
                String json = extractJson(response);
                return objectMapper.readValue(json, FieldJudgeResponse.class);
            } catch (Exception e) {
                log.warn("字段判定失败 ({}/{}) {}.{}: {}", attempt, props.getMaxRetries(),
                        ctx.getTableName(), ctx.getColumnName(), e.getMessage());
            }
        }
        return null;
    }

    private String fill(FieldContext ctx) {
        String subjectRule = props.isSubjectConsistency()
                ? "【主体一致性(强约束)】字段主体为「" + nz(ctx.getFieldSubject())
                  + "」。候选路径主体与之不一致的不得选中（除非无其他候选且语义高度吻合）。"
                : "";
        return template
                .replace("{{systemName}}", nz(ctx.getSystemName()))
                .replace("{{systemDesc}}", nz(ctx.getSystemDesc()))
                .replace("{{tableName}}", nz(ctx.getTableName()))
                .replace("{{tableChnName}}", nz(ctx.getTableChnName()))
                .replace("{{tableSubject}}", nz(ctx.getTableSubject()))
                .replace("{{columnName}}", nz(ctx.getColumnName()))
                .replace("{{columnChnName}}", nz(ctx.getColumnChnName()))
                .replace("{{normalizedName}}", nz(ctx.getNormalizedName()))
                .replace("{{fieldSubject}}", nz(ctx.getFieldSubject()))
                .replace("{{subjectRule}}", subjectRule)
                .replace("{{candidateList}}", buildCandidateList(ctx.getCandidates()));
    }

    private String buildCandidateList(List<CandidateV2> candidates) {
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
