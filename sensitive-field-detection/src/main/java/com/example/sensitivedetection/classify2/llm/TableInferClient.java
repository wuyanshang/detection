package com.example.sensitivedetection.classify2.llm;

import com.example.sensitivedetection.classify2.config.ClassifyV2Properties;
import com.example.sensitivedetection.classify2.dto.ColumnDTO;
import com.example.sensitivedetection.classify2.dto.TableClassifyRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 表级含义推断：整表一次 LLM 调用，输出表主体 + 每字段规范化含义/主体。
 * 失败返回 null（调用方降级为仅用原始字段名检索）。
 */
@Slf4j
@Component
public class TableInferClient {

    private final OpenAiChatModel chatModel;
    private final ClassifyV2Properties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String template;

    private final OpenAiChatOptions deterministicOptions = OpenAiChatOptions.builder()
            .temperature(0.0)
            .build();

    public TableInferClient(OpenAiChatModel chatModel, ClassifyV2Properties props) {
        this.chatModel = chatModel;
        this.props = props;
    }

    @PostConstruct
    public void init() {
        this.template = load("prompts/classify2-table-infer.txt");
        log.info("classify-v2 表级推断 prompt 加载完成");
    }

    public TableInferResponse infer(TableClassifyRequest req) {
        String prompt = fill(req);
        for (int attempt = 1; attempt <= props.getMaxRetries(); attempt++) {
            try {
                String response = ChatClient.create(chatModel)
                        .prompt().user(prompt)
                        .options(deterministicOptions)
                        .call().content();
                String json = extractJson(response);
                return objectMapper.readValue(json, TableInferResponse.class);
            } catch (Exception e) {
                log.warn("表级推断失败 ({}/{}) 表={}: {}", attempt, props.getMaxRetries(),
                        req.getTableName(), e.getMessage());
            }
        }
        return null;
    }

    private String fill(TableClassifyRequest req) {
        return template
                .replace("{{systemName}}", nz(req.getSystemName()))
                .replace("{{systemDesc}}", nz(req.getSystemDesc()))
                .replace("{{tableName}}", nz(req.getTableName()))
                .replace("{{tableChnName}}", nz(req.getTableChnName()))
                .replace("{{columnList}}", buildColumnList(req.getColumns()));
    }

    private String buildColumnList(List<ColumnDTO> columns) {
        List<Object> simplified = new ArrayList<>();
        if (columns != null) {
            for (ColumnDTO c : columns) {
                simplified.add(java.util.Map.of(
                        "columnName", nz(c.getColumnName()),
                        "columnChnName", nz(c.getColumnChnName())));
            }
        }
        try {
            return objectMapper.writeValueAsString(simplified);
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
