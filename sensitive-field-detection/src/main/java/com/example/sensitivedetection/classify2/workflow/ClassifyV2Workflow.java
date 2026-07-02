package com.example.sensitivedetection.classify2.workflow;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.sensitivedetection.classify2.config.ClassifyV2Properties;
import com.example.sensitivedetection.classify2.dto.ColumnDTO;
import com.example.sensitivedetection.classify2.dto.FieldClassifyRequest;
import com.example.sensitivedetection.classify2.dto.TableClassifyRequest;
import com.example.sensitivedetection.classify2.llm.TableInferClient;
import com.example.sensitivedetection.classify2.llm.TableInferResponse;
import com.example.sensitivedetection.classify2.model.FieldContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * classify-v2 编排入口：
 *  1) 表级含义推断（整表 1 次 LLM，锁定主体 + 规范化检索词）；
 *  2) 逐字段并行走 graph（召回 + 判定），注入表级推断结果。
 */
@Slf4j
@Component
public class ClassifyV2Workflow {

    private final CompiledGraph graph;
    private final TableInferClient tableInferClient;
    private final ClassifyV2Properties props;

    public ClassifyV2Workflow(@Qualifier("classifyV2Graph") CompiledGraph graph,
                              TableInferClient tableInferClient,
                              ClassifyV2Properties props) {
        this.graph = graph;
        this.tableInferClient = tableInferClient;
        this.props = props;
    }

    /** 表级分类：返回每字段结果。 */
    public TableResult classifyTable(TableClassifyRequest req) {
        List<ColumnDTO> columns = req.getColumns() == null ? List.of() : req.getColumns();

        // 1) 表级推断（可降级）
        TableInferResponse infer = null;
        if (props.isTableInferEnabled() && !columns.isEmpty()) {
            infer = tableInferClient.infer(req);
        }
        String tableSubject = infer == null ? null : infer.getTableSubject();
        Map<String, TableInferResponse.FieldItem> inferByCol = indexInference(infer);

        // 2) 逐字段并行
        int concurrency = Math.max(1, props.getConcurrencyLimit());
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<FieldContext>> futures = new ArrayList<>(columns.size());
            for (ColumnDTO col : columns) {
                TableInferResponse.FieldItem fi = inferByCol.get(nz(col.getColumnName()));
                FieldContext ctx = buildContext(req, col, tableSubject, fi);
                futures.add(executor.submit(() -> runQuietly(ctx)));
            }
            List<FieldContext> results = new ArrayList<>(columns.size());
            for (Future<FieldContext> f : futures) {
                try {
                    results.add(f.get(props.getLatchTimeoutMinutes(), TimeUnit.MINUTES));
                } catch (Exception e) {
                    log.error("v2 单字段执行失败: {}", e.getMessage());
                }
            }
            return new TableResult(tableSubject, results);
        } finally {
            executor.shutdown();
        }
    }

    /** 单字段分类（兜底）：包装成单列表复用同一链路，主体推断降级。 */
    public FieldContext classifyField(FieldClassifyRequest req) {
        TableClassifyRequest tableReq = new TableClassifyRequest();
        tableReq.setSystemName(req.getSystemName());
        tableReq.setSystemDesc(req.getSystemDesc());
        tableReq.setTableName(req.getTableName());
        tableReq.setTableChnName(req.getTableChnName());
        ColumnDTO col = new ColumnDTO();
        col.setColumnName(req.getColumnName());
        col.setColumnChnName(req.getColumnChnName());
        tableReq.setColumns(List.of(col));

        TableResult tr = classifyTable(tableReq);
        return tr.fields().isEmpty() ? buildContext(tableReq, col, null, null) : tr.fields().get(0);
    }

    private FieldContext runQuietly(FieldContext ctx) {
        try {
            Map<String, Object> initial = new HashMap<>();
            initial.put("result", ctx);
            Optional<OverAllState> finalState = graph.invoke(initial);
            if (finalState.isPresent()) {
                return (FieldContext) finalState.get().value("result").orElse(ctx);
            }
        } catch (Exception e) {
            log.error("v2 graph 执行失败 {}.{}: {}", ctx.getTableName(), ctx.getColumnName(), e.getMessage());
            ctx.setCategory(null);
            ctx.setReason("执行失败: " + e.getMessage());
            ctx.setSource("error");
        }
        return ctx;
    }

    private FieldContext buildContext(TableClassifyRequest req, ColumnDTO col,
                                      String tableSubject, TableInferResponse.FieldItem fi) {
        FieldContext ctx = new FieldContext();
        ctx.setSystemName(nz(req.getSystemName()));
        ctx.setSystemDesc(nz(req.getSystemDesc()));
        ctx.setTableName(nz(req.getTableName()));
        ctx.setTableChnName(nz(req.getTableChnName()));
        ctx.setColumnName(nz(col.getColumnName()));
        ctx.setColumnChnName(nz(col.getColumnChnName()));
        ctx.setTableSubject(tableSubject);
        if (fi != null) {
            ctx.setNormalizedName(blankToNull(fi.getNormalizedName()));
            ctx.setFieldSubject(blankToNull(fi.getSubject()));
            ctx.setConfidence(fi.getConfidence());
        }
        return ctx;
    }

    private Map<String, TableInferResponse.FieldItem> indexInference(TableInferResponse infer) {
        Map<String, TableInferResponse.FieldItem> map = new HashMap<>();
        if (infer != null && infer.getFields() != null) {
            for (TableInferResponse.FieldItem fi : infer.getFields()) {
                if (fi.getColumnName() != null) {
                    map.put(fi.getColumnName(), fi);
                }
            }
        }
        return map;
    }

    private String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 表级结果：表主体 + 每字段结果。 */
    public record TableResult(String tableSubject, List<FieldContext> fields) {
    }
}
