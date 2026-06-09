package com.example.sensitivedetection.sensitive;

import com.example.sensitivedetection.model.FieldResult;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class KeywordMatcher {

    private final Set<String> excludeKeywords = new HashSet<>();
    private final List<SensitiveKeyword> sensitiveKeywords = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadExcludeKeywords();
        loadSensitiveKeywords();
        log.info("关键词加载完成: 排除词 {} 个, 敏感词 {} 个", excludeKeywords.size(), sensitiveKeywords.size());
    }

    /**
     * 对字段进行关键词匹配
     * 匹配成功则设置 sensitiveSource、isSuspectedSensitive、catalogNode、sensitiveReason
     * 未匹配则不设置（留给 LLM 处理）
     */
    public void match(FieldResult result) {
        String fieldCn = result.getFieldCn();
        String fieldEn = result.getFieldEn();

        // 1. 排除词匹配（中文名精确匹配）
        if (fieldCn != null && !fieldCn.trim().isEmpty()) {
            if (excludeKeywords.contains(fieldCn.trim())) {
                result.setIsSuspectedSensitive("否");
                result.setSensitiveReason("命中技术类排除词");
                result.setSensitiveSource("关键词排除");
                return;
            }
        }

        // 2. 敏感词匹配 - 先匹配中文名（优先级高）
        if (fieldCn != null && !fieldCn.trim().isEmpty()) {
            for (SensitiveKeyword kw : sensitiveKeywords) {
                if ("cn".equals(kw.getType()) && fieldCn.trim().contains(kw.getKeyword())) {
                    result.setIsSuspectedSensitive("是");
                    result.setCatalogNode(kw.getCatalogNode());
                    result.setSensitiveReason("中文名命中关键词-" + kw.getKeyword());
                    result.setSensitiveSource("关键词");
                    return;
                }
            }
        }

        // 3. 敏感词匹配 - 再匹配英文名
        if (fieldEn != null && !fieldEn.trim().isEmpty()) {
            String fieldEnLower = fieldEn.trim().toLowerCase();
            for (SensitiveKeyword kw : sensitiveKeywords) {
                if ("en".equals(kw.getType()) && matchEnglishKeyword(fieldEnLower, kw.getKeyword())) {
                    result.setIsSuspectedSensitive("是");
                    result.setCatalogNode(kw.getCatalogNode());
                    result.setSensitiveReason("英文名命中关键词-" + kw.getKeyword());
                    result.setSensitiveSource("关键词");
                    return;
                }
            }
        }

        // 4. 都没命中 → sensitiveSource 保持 null，交给 LLM
    }

    /**
     * 英文关键词匹配：支持精确匹配和包含匹配
     * 例如 fieldEn=cust_name, keyword=name → 包含匹配
     * 例如 fieldEn=mobile, keyword=mobile → 精确匹配
     */
    private boolean matchEnglishKeyword(String fieldEnLower, String keyword) {
        String kwLower = keyword.toLowerCase();
        // 精确匹配
        if (fieldEnLower.equals(kwLower)) return true;
        // 包含匹配（以下划线或开头结尾为界）
        if (fieldEnLower.contains("_" + kwLower) || fieldEnLower.contains(kwLower + "_")) return true;
        if (fieldEnLower.startsWith(kwLower) || fieldEnLower.endsWith(kwLower)) return true;
        return false;
    }

    private void loadExcludeKeywords() {
        try {
            ClassPathResource resource = new ClassPathResource("exclude-keywords.txt");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        excludeKeywords.add(line);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("加载排除词文件失败: {}", e.getMessage());
        }
    }

    private void loadSensitiveKeywords() {
        try {
            ClassPathResource resource = new ClassPathResource("sensitive-keywords.csv");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean firstLine = true;
                while ((line = reader.readLine()) != null) {
                    if (firstLine) {
                        firstLine = false;
                        continue; // 跳过表头
                    }
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    String[] parts = line.split(",", 3);
                    if (parts.length == 3) {
                        SensitiveKeyword kw = new SensitiveKeyword();
                        kw.setType(parts[0].trim());
                        kw.setKeyword(parts[1].trim());
                        kw.setCatalogNode(parts[2].trim());
                        sensitiveKeywords.add(kw);
                    }
                }
            }
            // 按关键词长度降序排列，优先匹配更长的关键词
            sensitiveKeywords.sort((a, b) -> b.getKeyword().length() - a.getKeyword().length());
        } catch (Exception e) {
            log.warn("加载敏感词文件失败: {}", e.getMessage());
        }
    }

    @Data
    private static class SensitiveKeyword {
        private String type;       // cn / en
        private String keyword;
        private String catalogNode;
    }
}
