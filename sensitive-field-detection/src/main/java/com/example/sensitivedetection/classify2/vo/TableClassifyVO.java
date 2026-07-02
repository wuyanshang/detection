package com.example.sensitivedetection.classify2.vo;

import com.example.sensitivedetection.classify2.dto.TableClassifyRequest;
import com.example.sensitivedetection.classify2.workflow.ClassifyV2Workflow;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 表级分类响应。
 */
@Data
public class TableClassifyVO {

    private String systemName;
    private String tableName;
    private String tableChnName;
    /** 表级推断出的主体 */
    private String tableSubject;
    private List<FieldClassifyVO> fields;

    public static TableClassifyVO from(TableClassifyRequest req,
                                       ClassifyV2Workflow.TableResult result, boolean debug) {
        TableClassifyVO vo = new TableClassifyVO();
        vo.systemName = req.getSystemName();
        vo.tableName = req.getTableName();
        vo.tableChnName = req.getTableChnName();
        vo.tableSubject = result.tableSubject();
        vo.fields = result.fields().stream()
                .map(f -> FieldClassifyVO.from(f, debug))
                .collect(Collectors.toList());
        return vo;
    }
}
