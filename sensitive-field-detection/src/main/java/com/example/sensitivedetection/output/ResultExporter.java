package com.example.sensitivedetection.output;

import com.example.sensitivedetection.model.FieldResult;
import com.example.sensitivedetection.model.TableResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Component
public class ResultExporter {

    // Sheet1：字段级检测结果
    private static final String[] FIELD_HEADERS = {
            "批次号", "系统英文名", "系统中文名", "表英文名", "表中文名",
            "字段英文名", "字段中文名", "不通过规则编号", "不通过规则描述", "质检结果",
            "是否疑似敏感", "目录节点", "敏感原因", "敏感来源"
    };

    // Sheet2：表级检测结果
    private static final String[] TABLE_HEADERS = {
            "系统英文名", "系统中文名", "表英文名", "表中文名", "不通过规则编号", "不通过规则描述"
    };

    public void exportToExcel(List<FieldResult> fieldResults, List<TableResult> tableResults, String outputPath) throws IOException {
        Path path = Paths.get(outputPath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            // 公共样式
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle interceptStyle = createInterceptStyle(workbook);
            CellStyle sensitiveStyle = createSensitiveStyle(workbook);

            // ========== Sheet1: 字段级检测结果 ==========
            writeFieldSheet(workbook, fieldResults, headerStyle, interceptStyle, sensitiveStyle);

            // ========== Sheet2: 表级检测结果 ==========
            writeTableSheet(workbook, tableResults, headerStyle, interceptStyle);

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                workbook.write(fos);
            }
        }

        log.info("结果导出完成: {}, 字段 {} 条, 表 {} 条", outputPath, fieldResults.size(), tableResults.size());
    }

    private void writeFieldSheet(Workbook workbook, List<FieldResult> results,
                                 CellStyle headerStyle, CellStyle interceptStyle, CellStyle sensitiveStyle) {
        Sheet sheet = workbook.createSheet("字段检测结果");

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < FIELD_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(FIELD_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        for (int i = 0; i < results.size(); i++) {
            FieldResult r = results.get(i);
            Row row = sheet.createRow(i + 1);

            row.createCell(0).setCellValue(safeStr(r.getBatchNo()));
            row.createCell(1).setCellValue(safeStr(r.getSystemEn()));
            row.createCell(2).setCellValue(safeStr(r.getSystemCn()));
            row.createCell(3).setCellValue(safeStr(r.getTableEn()));
            row.createCell(4).setCellValue(safeStr(r.getTableCn()));
            row.createCell(5).setCellValue(safeStr(r.getFieldEn()));
            row.createCell(6).setCellValue(safeStr(r.getFieldCn()));
            row.createCell(7).setCellValue(safeStr(r.getRuleCodesStr()));
            row.createCell(8).setCellValue(safeStr(r.getRuleDescsStr()));
            row.createCell(9).setCellValue(safeStr(r.getQualityResult()));
            row.createCell(10).setCellValue(safeStr(r.getIsSuspectedSensitive()));
            row.createCell(11).setCellValue(safeStr(r.getCatalogNode()));
            row.createCell(12).setCellValue(safeStr(r.getSensitiveReason()));
            row.createCell(13).setCellValue(safeStr(r.getSensitiveSource()));

            CellStyle rowStyle = null;
            if ("拦截".equals(r.getQualityResult())) {
                rowStyle = interceptStyle;
            } else if ("是".equals(r.getIsSuspectedSensitive())) {
                rowStyle = sensitiveStyle;
            }
            if (rowStyle != null) {
                for (int j = 0; j < FIELD_HEADERS.length; j++) {
                    row.getCell(j).setCellStyle(rowStyle);
                }
            }
        }

        for (int i = 0; i < FIELD_HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writeTableSheet(Workbook workbook, List<TableResult> results,
                                 CellStyle headerStyle, CellStyle interceptStyle) {
        Sheet sheet = workbook.createSheet("表检测结果");

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < TABLE_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(TABLE_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }

        for (int i = 0; i < results.size(); i++) {
            TableResult r = results.get(i);
            Row row = sheet.createRow(i + 1);

            row.createCell(0).setCellValue(safeStr(r.getSystemEn()));
            row.createCell(1).setCellValue(safeStr(r.getSystemCn()));
            row.createCell(2).setCellValue(safeStr(r.getTableEn()));
            row.createCell(3).setCellValue(safeStr(r.getTableCn()));
            row.createCell(4).setCellValue(safeStr(r.getRuleCodesStr()));
            row.createCell(5).setCellValue(safeStr(r.getRuleDescsStr()));

            if (!r.getRuleCodes().isEmpty()) {
                for (int j = 0; j < TABLE_HEADERS.length; j++) {
                    row.getCell(j).setCellStyle(interceptStyle);
                }
            }
        }

        for (int i = 0; i < TABLE_HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createInterceptStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createSensitiveStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private String safeStr(String s) {
        return s == null ? "" : s;
    }
}
