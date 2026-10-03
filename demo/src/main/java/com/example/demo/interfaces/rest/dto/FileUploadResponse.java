package com.example.demo.interfaces.rest.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 文件上传成功响应。
 */
@Data
@Builder
public class FileUploadResponse {

    private String key;
    private String name;
    private String url;
    private long size;
    private String contentType;
    private boolean image;
}
