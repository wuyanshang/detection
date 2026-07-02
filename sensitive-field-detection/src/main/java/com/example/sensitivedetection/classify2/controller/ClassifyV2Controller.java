package com.example.sensitivedetection.classify2.controller;

import com.example.sensitivedetection.classify2.config.ClassifyV2Properties;
import com.example.sensitivedetection.classify2.dto.FieldClassifyRequest;
import com.example.sensitivedetection.classify2.dto.TableClassifyRequest;
import com.example.sensitivedetection.classify2.model.FieldContext;
import com.example.sensitivedetection.classify2.vo.FieldClassifyVO;
import com.example.sensitivedetection.classify2.vo.TableClassifyVO;
import com.example.sensitivedetection.classify2.workflow.ClassifyV2Workflow;
import com.example.sensitivedetection.security.vo.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * classify-v2 分类接口（新增，独立于 /api/security/classify）。
 * 表级推断 + 新索引混合召回 + 满上下文判定（含主体一致性）。
 */
@Slf4j
@RestController
@RequestMapping("/api/security/v2")
public class ClassifyV2Controller {

    private final ClassifyV2Workflow workflow;
    private final ClassifyV2Properties props;

    public ClassifyV2Controller(ClassifyV2Workflow workflow, ClassifyV2Properties props) {
        this.workflow = workflow;
        this.props = props;
    }

    /** 表级分类：一次一整张表 + 字段清单 → 每字段结果（推荐）。 */
    @PostMapping("/classify-table")
    public ApiResponse<TableClassifyVO> classifyTable(
            @Valid @RequestBody TableClassifyRequest request,
            @RequestParam(value = "debug", defaultValue = "false") boolean debug) {
        int size = request.getColumns() == null ? 0 : request.getColumns().size();
        if (size > props.getColumnsLimit()) {
            return ApiResponse.error(400, "单表字段超过上限 " + props.getColumnsLimit() + " 个，请分片提交");
        }
        log.info("classify-v2 表级请求 表={} 字段数={}", request.getTableName(), size);
        ClassifyV2Workflow.TableResult result = workflow.classifyTable(request);
        return ApiResponse.ok(TableClassifyVO.from(request, result, debug));
    }

    /** 单字段分类（兜底）：拿不到同表上下文时用，主体推断降级。 */
    @PostMapping("/classify-field")
    public ApiResponse<FieldClassifyVO> classifyField(
            @Valid @RequestBody FieldClassifyRequest request,
            @RequestParam(value = "debug", defaultValue = "false") boolean debug) {
        FieldContext ctx = workflow.classifyField(request);
        return ApiResponse.ok(FieldClassifyVO.from(ctx, debug));
    }
}
