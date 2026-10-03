package com.example.obs.core;

import java.io.InputStream;

/**
 * 对象存储下载结果 DTO。
 *
 * @param stream       文件二进制流（调用方负责关闭）
 * @param contentType  Content-Type
 * @param contentLength 文件大小（字节），-1 表示未知
 */
public record ObsDownloadResult(InputStream stream, String contentType, long contentLength) {
}
