package com.example.sensitivedetection.ambiguity.gate;

import com.example.sensitivedetection.ambiguity.model.Ambiguity;
import com.example.sensitivedetection.ambiguity.model.AmbiguityResult;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 确定性门控层（纯代码，无 LLM）。
 * 职责：
 *  1) B000005 备用类字段待核实 —— 命中即短路，不进 stage1/stage2；
 *  2) 计算规范类候选 hitB2（高风险词表）、hitB4（中文名英文字母 >=2）。
 */
@Component
public class AmbiguityGate {

    /** 唯一高风险词表（B000002 门控；精确字面子串匹配，禁止近义词推断） */
    private static final List<String> HIGH_RISK_WORDS =
            List.of("姓名", "状态", "日期", "金额", "关系", "编号", "利率", "地址");

    /** 备用类字段：中文（子串匹配） */
    private static final List<String> SPARE_CN =
            List.of("备用", "预留", "占位", "保留", "扩展", "冷备", "备份", "预备", "待用", "冗余");

    /** 备用类字段：英文（切词后整词匹配，避免 ext 误伤 context、pad 误伤 update） */
    private static final Set<String> SPARE_EN = Set.of(
            "spare", "reserve", "reserved", "rsv", "rsrv", "filler", "fill",
            "bak", "backup", "ext", "pad", "padding", "unused", "dummy");

    /**
     * 对单个结果执行门控，原地写入标记。
     */
    public void apply(AmbiguityResult r) {
        String fieldCn = r.getFieldCn() == null ? "" : r.getFieldCn();

        // 1. B000005 备用类字段 → 短路
        if (isSpare(fieldCn, r.getFieldEn())) {
            r.setSpare(true);
            r.addAmbiguity(new Ambiguity("B000005", "字段疑似备用/预留/占位类，需人工核实", "确认是否占位字段，若启用建议规范命名，否则清理"));
            r.setHasAmbiguity(true);
            r.setSummary("备用类字段待核实");
            r.setSemanticDone(true);
            r.setSource("备用短路");
            return;
        }

        // 2. 规范类候选门控
        boolean hitB2 = HIGH_RISK_WORDS.stream().anyMatch(fieldCn::contains);
        boolean hitB4 = countLetters(fieldCn) >= 2;
        r.setGateB2(hitB2);
        r.setGateB4(hitB4);
    }

    private boolean isSpare(String fieldCn, String fieldEn) {
        boolean spareCn = SPARE_CN.stream().anyMatch(fieldCn::contains);
        if (spareCn) {
            return true;
        }
        if (fieldEn == null || fieldEn.isBlank()) {
            return false;
        }
        // 驼峰拆分 + 转小写 + 按非字母切词后整词比较
        String normalized = fieldEn.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
        return Arrays.stream(normalized.split("[^a-z]+"))
                .filter(t -> !t.isEmpty())
                .anyMatch(SPARE_EN::contains);
    }

    private long countLetters(String s) {
        return s.chars()
                .filter(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))
                .count();
    }
}
