package com.example.sensitivedetection.model;

import lombok.Data;

import java.util.List;

@Data
public class DetectionResult {

    private List<FieldResult> fieldResults;
    private List<TableResult> tableResults;

    public DetectionResult(List<FieldResult> fieldResults, List<TableResult> tableResults) {
        this.fieldResults = fieldResults;
        this.tableResults = tableResults;
    }
}
