package com.example.sensitivedetection.ambiguity.controller;

import com.example.sensitivedetection.ambiguity.model.AmbiguityResult;
import com.example.sensitivedetection.ambiguity.output.AmbiguityExporter;
import com.example.sensitivedetection.ambiguity.workflow.AmbiguityWorkflow;
import com.example.sensitivedetection.model.FieldInput;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 语义歧义检测入口（与敏感检测并存的独立工作流）。
 * 上传 Excel：系统英文名, 系统中文名, 表英文名, 表中文名, 字段英文名, 字段中文名
 */
@Slf4j
@RestController
@RequestMapping("/api/ambiguity")
public class AmbiguityController {

    private static final String OUTPUT_FILE = "output/ambiguity_result.xlsx";

    private final AmbiguityWorkflow workflow;
    private final AmbiguityExporter exporter;

    public AmbiguityController(AmbiguityWorkflow workflow, AmbiguityExporter exporter) {
        this.workflow = workflow;
        this.exporter = exporter;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(@RequestParam("file") MultipartFile file) {
        try {
            List<FieldInput> inputs = readExcel(file.getInputStream());
            log.info("歧义检测：读取 {} 条字段", inputs.size());
            if (inputs.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "输入数据为空"));
            }

            List<AmbiguityResult> results = workflow.execute(inputs);
            exporter.exportToExcel(results, OUTPUT_FILE);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "歧义检测完成");
            response.put("outputFile", OUTPUT_FILE);
            response.put("totalFields", results.size());
            response.put("ambiguousFields", results.stream().filter(AmbiguityResult::isHasAmbiguity).count());
            response.put("cacheHit", results.stream().filter(AmbiguityResult::isCacheHit).count());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("歧义检测失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "歧义检测失败: " + e.getMessage()));
        }
    }

    private List<FieldInput> readExcel(InputStream inputStream) throws Exception {
        List<FieldInput> inputs = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                FieldInput input = new FieldInput();
                input.setSystemEn(getCellValue(row, 0));
                input.setSystemCn(getCellValue(row, 1));
                input.setTableEn(getCellValue(row, 2));
                input.setTableCn(getCellValue(row, 3));
                input.setFieldEn(getCellValue(row, 4));
                input.setFieldCn(getCellValue(row, 5));
                inputs.add(input);
            }
        }
        return inputs;
    }

    private String getCellValue(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        String value = cell.getStringCellValue();
        return value == null ? "" : value.trim();
    }
}
