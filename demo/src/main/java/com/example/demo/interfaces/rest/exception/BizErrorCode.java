package com.example.demo.interfaces.rest.exception;

import lombok.Getter;

/**
 * 业务错误码枚举。
 */
@Getter
public enum BizErrorCode {

    FILE_EMPTY(40001, "上传文件为空"),
    TYPE_NOT_ALLOWED(40002, "文件类型不被允许"),
    SIZE_EXCEEDED(40003, "文件大小超出限制"),
    KEY_INVALID(40004, "文件 key 非法"),
    PART_NUMBER_INVALID(40005, "分片序号非法（1-10000）"),
    PART_DATA_INVALID(40006, "分片数据非法（非空且不超过单片大小）"),
    SIZE_MISMATCH(40007, "云端分片总量与声明大小不一致"),
    SESSION_NOT_UPLOADING(40008, "分片会话不在上传中状态"),
    PART_MD5_INVALID(40009, "分片 Content-MD5 缺失、格式错误或与内容不一致"),
    CONTENT_HASH_INVALID(40010, "文件内容哈希必须是 64 位 SHA-256 十六进制字符串"),
    CONTENT_HASH_MISMATCH(40011, "云端文件内容与声明的 SHA-256 不一致"),
    NOT_FOUND(40404, "文件不存在"),
    QINIU_ERROR(50001, "对象存储服务异常"),
    DOWNLOAD_ERROR(50002, "文件下载失败");

    private final int code;
    private final String message;

    BizErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
