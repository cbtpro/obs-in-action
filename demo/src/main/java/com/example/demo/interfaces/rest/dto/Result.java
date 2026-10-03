package com.example.demo.interfaces.rest.dto;

import com.example.demo.interfaces.rest.exception.BizErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一返回结构。
 */
@Data
@AllArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "ok", data);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(BizErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    public static <T> Result<T> fail(BizErrorCode errorCode, String message) {
        return fail(errorCode.getCode(), message);
    }
}
