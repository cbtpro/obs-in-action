package com.example.obs.core;

import lombok.Builder;

/**
 * 对象存储上传结果 DTO。
 */
@Builder
public record ObsUploadResult(
        /** 存储 key */
        String key,
        /** 可访问 url（公网或私有下载地址） */
        String url,
        /** 文件大小（字节） */
        long size,
        /** Content-Type */
        String contentType
) {
}
