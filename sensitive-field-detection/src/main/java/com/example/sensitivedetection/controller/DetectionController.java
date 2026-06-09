package com.example.sensitivedetection.runner;

import com.example.sensitivedetection.config.DetectionProperties;
import com.example.sensitivedetection.model.DetectionResult;
import com.example.sensitivedetection.model.FieldInput;
import com.example.sensitivedetection.model.FieldResult;
import com.example.sensitivedetection.model.TableResult;
import com.example.sensitivedetection.output.ResultExporter;
import com.example.sensitivedetection.workflow.DetectionWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.apache.poi.ss.usermodel.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/detection")
public class DetectionController {

    private final DetectionWorkflow workflow;
    private final ResultExporter exporter;
    private final DetectionProperties properties;

    public DetectionController(DetectionWorkflow workflow,
                               ResultExporter exporter,
                               DetectionProperties properties) {
        this.workflow = workflow;
        this.exporter = exporter;
        this.properties = properties;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run(@RequestParam("file") MultipartFile file) {
        try {
            log.info("接收到上传文件: {}", file.getOriginalFilename());
            List<FieldInput> inputs = readExcel(file.getInputStream());
            log.info("读取到 {} 条字段记录", inputs.size());

            if (inputs.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "输入数据为空"));
            }

            DetectionResult detectionResult = workflow.execute(inputs);
            List<FieldResult> fieldResults = detectionResult.getFieldResults();
            List<TableResult> tableResults = detectionResult.getTableResults();

            String outputFile = properties.getOutputFile();
            exporter.exportToExcel(fieldResults, tableResults, outputFile);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "检测完成");
            response.put("outputFile", outputFile);
            response.put("totalFields", fieldResults.size());
            response.put("totalTables", tableResults.size());
            response.put("interceptedFields", fieldResults.stream().filter(r -> "拦截".equals(r.getQualityResult())).count());
            response.put("suspectedSensitive", fieldResults.stream().filter(r -> "是".equals(r.getIsSuspectedSensitive())).count());
            response.put("interceptedTables", tableResults.stream().filter(r -> !r.getRuleCodes().isEmpty()).count());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("检测失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "检测失败: " + e.getMessage()));
        }
    }

    /**
     * 读取 Excel 文件（.xlsx / .xls）
     * 表头行：系统英文名, 系统中文名, 表英文名, 表中文名, 字段英文名, 字段中文名
     */
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
