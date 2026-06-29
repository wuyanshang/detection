package com.example.sensitivedetection.security.controller;

import com.example.sensitivedetection.security.config.SecurityClassificationProperties;
import com.example.sensitivedetection.security.dto.SecurityClassificationInputDTO;
import com.example.sensitivedetection.security.model.SecurityClassificationResult;
import com.example.sensitivedetection.security.vo.ApiResponse;
import com.example.sensitivedetection.security.vo.SecurityClassificationOutputVO;
import com.example.sensitivedetection.security.workflow.SecurityClassificationWorkflow;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 安全分类分级接口（对应设计文档 3）。
 */
@Slf4j
@RestController
@RequestMapping("/api/security")
public class SecurityClassificationController {

    private final SecurityClassificationWorkflow workflow;
    private final SecurityClassificationProperties props;

    public SecurityClassificationController(SecurityClassificationWorkflow workflow,
                                            SecurityClassificationProperties props) {
        this.workflow = workflow;
        this.props = props;
    }

    @PostMapping("/classify")
    public ApiResponse<SecurityClassificationOutputVO> classify(
            @Valid @RequestBody SecurityClassificationInputDTO request,
            @RequestParam(value = "debug", defaultValue = "false") boolean debug) {
        SecurityClassificationResult result = workflow.classify(request);
        return ApiResponse.ok(SecurityClassificationOutputVO.from(result, debug));
    }

    @PostMapping("/classify/batch")
    public ApiResponse<List<SecurityClassificationOutputVO>> classifyBatch(
            @Valid @RequestBody List<SecurityClassificationInputDTO> requests,
            @RequestParam(value = "debug", defaultValue = "false") boolean debug) {
        if (requests.size() > props.getBatchSizeLimit()) {
            return ApiResponse.error(400,
                    "单批超过上限 " + props.getBatchSizeLimit() + " 条，请分片提交");
        }
        log.info("安全分级批量请求 {} 条", requests.size());
        List<SecurityClassificationOutputVO> data = workflow.classifyBatch(requests).stream()
                .map(r -> SecurityClassificationOutputVO.from(r, debug))
                .collect(Collectors.toList());
        return ApiResponse.ok(data);
    }
}
