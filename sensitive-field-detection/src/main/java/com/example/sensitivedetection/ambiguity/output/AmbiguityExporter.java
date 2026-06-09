package com.example.sensitivedetection.ambiguity.output;

import com.example.sensitivedetection.ambiguity.model.Ambiguity;
import com.example.sensitivedetection.ambiguity.model.AmbiguityResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AmbiguityExporter {

    private static final String[] HEADERS = {
            "系统中文名", "系统英文名", "表中文名", "表英文名", "字段中文名", "字段英文名",
            "是否有歧义", "命中规则", "歧义详情", "建议", "消歧路径", "结论来源", "缓存命中"
    };

    public void exportToExcel(List<AmbiguityResult> results, String outputPath) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("歧义检测结果");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }

            int rowIdx = 1;
            for (AmbiguityResult r : results) {
                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                set(row, c++, r.getSystemCn());
                set(row, c++, r.getSystemEn());
                set(row, c++, r.getTableCn());
                set(row, c++, r.getTableEn());
                set(row, c++, r.getFieldCn());
                set(row, c++, r.getFieldEn());
                set(row, c++, r.isHasAmbiguity() ? "是" : "否");
                set(row, c++, joinCodes(r.getAmbiguities()));
                set(row, c++, joinDetails(r.getAmbiguities()));
                set(row, c++, joinSuggestions(r.getAmbiguities()));
                set(row, c++, r.getDisambiguationPath());
                set(row, c++, r.getSource());
                set(row, c++, r.isCacheHit() ? "是" : "否");
            }

            File file = new File(outputPath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                workbook.write(fos);
            }
            log.info("歧义检测结果已导出: {}", outputPath);
        } catch (Exception e) {
            log.error("导出歧义结果失败: {}", e.getMessage(), e);
            throw new RuntimeException("导出歧义结果失败", e);
        }
    }

    private void set(Row row, int col, String value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
    }

    private String joinCodes(List<Ambiguity> list) {
        return list.stream().map(Ambiguity::getRuleCode).collect(Collectors.joining(";"));
    }

    private String joinDetails(List<Ambiguity> list) {
        return list.stream().map(a -> a.getRuleCode() + ":" + nz(a.getDetail())).collect(Collectors.joining(" | "));
    }

    private String joinSuggestions(List<Ambiguity> list) {
        return list.stream().map(a -> nz(a.getSuggestion())).filter(s -> !s.isEmpty()).collect(Collectors.joining(" | "));
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
