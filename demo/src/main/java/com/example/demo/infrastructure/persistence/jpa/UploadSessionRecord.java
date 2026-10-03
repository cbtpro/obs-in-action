package com.example.demo.infrastructure.persistence.jpa;

import com.example.demo.domain.model.UploadSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分片上传会话 JPA 实体（基础设施层）。
 */
@Entity
@Table(name = "upload_session", indexes = {
        @Index(name = "idx_upload_session_fp", columnList = "fingerprint"),
        @Index(name = "idx_upload_session_status_created", columnList = "status, created_at")
})
@Data
public class UploadSessionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 128)
    @Column(name = "fingerprint", nullable = false, length = 128)
    private String fingerprint;

    @NotBlank
    @Size(min = 64, max = 64)
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @NotBlank
    @Size(max = 255)
    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @NotBlank
    @Size(max = 255)
    @Column(name = "upload_id", nullable = false, length = 255)
    private String uploadId;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String name;

    @NotNull
    @Column(nullable = false)
    private long size;

    @Column(name = "content_type", length = 200)
    private String contentType;

    @NotNull
    @Column(nullable = false)
    private boolean image;

    @NotNull
    @Column(name = "chunk_size", nullable = false)
    private int chunkSize;

    /** 会话状态：UPLOADING / COMPLETED / ABORTED */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UploadSessionStatus status;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
