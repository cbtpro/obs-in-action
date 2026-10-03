package com.example.obs.core;

/**
 * 已上传分片的描述（云端视角）。
 * partNumber 为分片序号（从 1 开始）；etag 是云端返回的分片标识，complete 时原样回传；
 * size 为该分片字节数，用于进度计算。
 */
public record ObsPartInfo(int partNumber, String etag, long size) {
}
