package com.example.sensitivedetection.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TableResult {

    private String batchNo;
    private String systemCn;
    private String systemEn;
    private String tableCn;
    private String tableEn;

    // ---- E 规则质检结果（编号和描述分开存储） ----
    private List<String> ruleCodes = new ArrayList<>();
    private List<String> ruleDescs = new ArrayList<>();

    public void addRule(String ruleCode, String ruleDesc) {
        ruleCodes.add(ruleCode);
        ruleDescs.add(ruleDesc);
    }

    public String getRuleCodesStr() {
        return String.join(";", ruleCodes);
    }

    public String getRuleDescsStr() {
        return String.join(";", ruleDescs);
    }
}
