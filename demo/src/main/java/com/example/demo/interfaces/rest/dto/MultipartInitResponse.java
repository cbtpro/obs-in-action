package com.example.demo.interfaces.rest.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 分片上传 init 响应体。
 * instantUpload=true 表示内容秒传命中；否则 resumed=true 表示命中未完成会话，
 * uploadedParts 为云端已确认分片。
 */
@Data
@Builder
public class MultipartInitResponse {

    private Long sessionId;
    private int chunkSize;
    private boolean resumed;
    /** true 表示内容哈希命中已有文件，无需创建会话或上传分片 */
    private boolean instantUpload;
    /** 秒传命中时返回已有文件；普通 init 时为 null */
    private FileUploadResponse file;
    private List<MultipartPartItem> uploadedParts;
}
