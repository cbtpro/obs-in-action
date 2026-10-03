package com.example.obs.core;

import lombok.Builder;

/**
 * 对象存储上传请求 DTO。
 * 不依赖 Spring Web（MultipartFile），纯 Java byte[]，便于各厂商实现对接。
 */
@Builder
public record ObsUploadRequest(
        /** 文件二进制数据 */
        byte[] data,
        /** 存储 key（由业务层生成） */
        String key,
        /** Content-Type */
        String contentType,
        /** 原始文件名 */
        String originalName,
        /** 文件大小（字节） */
        long size
) {
}
