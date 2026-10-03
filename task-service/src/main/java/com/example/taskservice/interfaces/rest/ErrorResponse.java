package com.example.taskservice.interfaces.rest;

/**
 * 通用错误响应体。
 * 前端 HttpClient 从 message 字段提取错误提示，字段名需保持稳定。
 *
 * @param code    错误码（业务语义标识）
 * @param message 人类可读的错误描述
 */
public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message);
    }
}
