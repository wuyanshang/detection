package com.example.sensitivedetection.ambiguity;

import java.util.List;

/**
 * 歧义检测全局常量。
 */
public final class AmbiguityConstants {

    private AmbiguityConstants() {
    }

    /**
     * Prompt 版本号。改动 stage1/stage2 规则时务必同步递增：
     * 它已纳入缓存 key 的哈希，递增后旧缓存自动失效（不会读到过期结论）。
     */
    public static final String PROMPT_VERSION = "amb-v4";

    // 规则码
    public static final String B000001 = "B000001"; // 中英文矛盾（理解类）
    public static final String B000002 = "B000002"; // 缺业务限定（规范类）
    public static final String B000003 = "B000003"; // 枚举值无含义（理解类）
    public static final String B000004 = "B000004"; // 缩写多义（规范类）
    public static final String B000005 = "B000005"; // 备用类字段待核实

    /**
     * stage2 可按需加载的规则及其拼接顺序（B000005 由代码短路，不在 stage2）。
     * 对应 resources/prompts/rules/{code}.txt 片段文件。
     */
    public static final List<String> STAGE2_RULES = List.of(B000001, B000002, B000003, B000004);
}
