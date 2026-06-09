package com.example.sensitivedetection.rule;

import com.example.sensitivedetection.model.TableResult;

import java.util.List;

public interface TableRule {

    String getRuleCode();

    String getRuleDesc();

    void check(List<TableResult> results);
}
