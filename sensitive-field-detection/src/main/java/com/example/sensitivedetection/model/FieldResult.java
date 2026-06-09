package com.example.sensitivedetection.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FieldResult {

    // ---- 输入字段 ----
    private String batchNo;
    private String systemCn;
    private String systemEn;
    private String tableCn;
    private String tableEn;
    private String fieldCn;
    private String fieldEn;

    // ---- A 规则质检结果（编号和描述分开存储） ----
    private List<String> ruleCodes = new ArrayList<>();
    private List<String> ruleDescs = new ArrayList<>();
    private String qualityResult; // 通过 / 拦截

    // ---- B000001 敏感识别结果 ----
    private String isSuspectedSensitive; // 是 / 否 / 不确定
    private String catalogNode;          // 姓名 / 证件号码 / ... / 不确定
    private String sensitiveReason;
    private String sensitiveSource;      // 关键词 / LLM / 关键词排除

    /**
     * 添加一条质检规则触发记录
     */
    public void addQualityRule(String ruleCode, String ruleDesc) {
        ruleCodes.add(ruleCode);
        ruleDescs.add(ruleDesc);
    }

    /**
     * 不通过规则编号（分号分隔）
     */
    public String getRuleCodesStr() {
        return String.join(";", ruleCodes);
    }

    /**
     * 不通过规则描述（分号分隔）
     */
    public String getRuleDescsStr() {
        return String.join(";", ruleDescs);
    }

    /**
     * 根据是否有触发的质检规则，自动计算质检结果
     */
    public void computeQualityResult() {
        this.qualityResult = ruleCodes.isEmpty() ? "通过" : "拦截";
    }

    /**
     * 从 FieldInput 构建 FieldResult
     */
    public static FieldResult from(FieldInput input, String batchNo) {
        FieldResult result = new FieldResult();
        result.setBatchNo(batchNo);
        result.setSystemCn(input.getSystemCn());
        result.setSystemEn(input.getSystemEn());
        result.setTableCn(input.getTableCn());
        result.setTableEn(input.getTableEn());
        result.setFieldCn(input.getFieldCn());
        result.setFieldEn(input.getFieldEn());
        return result;
    }
}
