package com.example.demo.domain.model;

/**
 * 分片上传会话状态。
 * UPLOADING：进行中（断点续传的候选）
 * COMPLETED：已完成，对象已拼接为完整文件，会话仅留作幂等核对
 * ABORTED：已作废（主动终止或云端会话过期）
 */
public enum UploadSessionStatus {
    UPLOADING,
    COMPLETED,
    ABORTED
}
