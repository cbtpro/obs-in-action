package com.example.demo.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分片上传会话领域实体。
 * 记录一次分片上传的云端会话标识（uploadId）与文件元数据，是断点续传的服务端锚点：
 * 客户端凭指纹找回会话，凭云端 listParts 的已确认分片决定续传起点。
 */
@Data
@Builder
public class UploadSession {

    private Long id;
    /** 客户端计算的文件指纹（同名同大小同修改时间视为同一文件） */
    private String fingerprint;
    /** 文件完整内容的 SHA-256 十六进制摘要 */
    private String contentHash;
    /** 对象存储 key（init 时服务端生成） */
    private String storageKey;
    /** 云端分片会话标识 */
    private String uploadId;
    /** 原始文件名 */
    private String name;
    /** 声明的文件大小（字节），complete 时与云端分片总量核对 */
    private long size;
    /** Content-Type */
    private String contentType;
    /** 是否图片 */
    private boolean image;
    /** 单片大小（字节），init 时从存储实现取，续传客户端沿用 */
    private int chunkSize;
    /** 会话状态 */
    private UploadSessionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
