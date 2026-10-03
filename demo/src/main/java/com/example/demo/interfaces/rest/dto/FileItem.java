package com.example.demo.interfaces.rest.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 已上传文件列表项。
 */
@Data
@Builder
public class FileItem {

    private String key;
    private String name;
    private long size;
    private String mimeType;
    private long putTime;
}
