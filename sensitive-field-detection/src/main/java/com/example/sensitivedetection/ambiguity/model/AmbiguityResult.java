package com.example.sensitivedetection.ambiguity.model;

import com.example.sensitivedetection.model.FieldInput;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 单字段的语义歧义检测结果。独立于敏感检测的 FieldResult。
 */
@Data
public class AmbiguityResult {

    // ---- 输入 ----
    private String batchNo;
    private String systemCn;
    private String systemEn;
    private String tableCn;
    private String tableEn;
    private String fieldCn;
    private String fieldEn;
    private String abbreviationMappings = "无";

    // ---- 缓存 ----
    private String cacheKey;     // md5(promptVersion + 输入)
    private boolean cacheHit;    // 本次是否命中缓存

    // ---- 门控（代码层，确定性） ----
    private boolean spare;       // 命中备用词表（B000005 短路）
    private boolean gateB2;      // 命中高风险词表 → B000002 候选
    private boolean gateB4;      // 中文名英文字母 >=2 → B000004 候选
    private boolean semanticDone;// 短路标记：无需进 stage1/stage2

    // ---- stage1 ----
    private Boolean disambiguationSuccess; // null=未跑
    private String disambiguationPath;

    // ---- 待判规则集合（传给 stage2） ----
    private Set<String> rulesToCheck = new LinkedHashSet<>();

    // ---- 最终结论 ----
    private boolean hasAmbiguity;
    private List<Ambiguity> ambiguities = new ArrayList<>();
    private String summary;
    private String source; // 缓存 / 备用短路 / 门控直通 / LLM

    public void addAmbiguity(Ambiguity a) {
        this.ambiguities.add(a);
    }

    public boolean hasRule(String ruleCode) {
        return ambiguities.stream().anyMatch(a -> ruleCode.equals(a.getRuleCode()));
    }

    public static AmbiguityResult from(FieldInput input, String batchNo, String promptVersion) {
        AmbiguityResult r = new AmbiguityResult();
        r.batchNo = batchNo;
        r.systemCn = input.getSystemCn();
        r.systemEn = input.getSystemEn();
        r.tableCn = input.getTableCn();
        r.tableEn = input.getTableEn();
        r.fieldCn = input.getFieldCn();
        r.fieldEn = input.getFieldEn();
        r.cacheKey = computeCacheKey(promptVersion, r);
        return r;
    }

    /**
     * 缓存 key = md5(promptVersion + 表中英文名 + 字段中英文名)。
     * - 把 promptVersion 纳入哈希：改规则即换 key，旧缓存自然失效，绝不会读到过期结论。
     * - 不含缩写映射：它是派生信息，不参与缓存身份。
     */
    public static String computeCacheKey(String promptVersion, AmbiguityResult r) {
        String raw = String.join("\u0001",
                nz(promptVersion),
                nz(r.tableCn), nz(r.tableEn),
                nz(r.fieldCn), nz(r.fieldEn));
        return md5(raw);
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算缓存 key 失败", e);
        }
    }
}
