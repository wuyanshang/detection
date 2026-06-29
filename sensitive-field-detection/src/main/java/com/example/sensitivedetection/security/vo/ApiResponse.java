package com.example.sensitivedetection.security.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一响应包装（对应设计文档 3.1）。
 */
@Data
@AllArgsConstructor
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
