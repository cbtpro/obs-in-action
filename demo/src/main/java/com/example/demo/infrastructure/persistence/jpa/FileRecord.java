package com.example.demo.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 文件元数据 JPA 实体（基础设施层）。
 */
@Entity
@Table(name = "file_record", indexes = {
        @Index(name = "idx_file_record_content_hash_size", columnList = "content_hash, size")
})
@Data
public class FileRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "storage_key", nullable = false, unique = true, length = 255)
    private String storageKey;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String name;

    @NotNull
    @Column(nullable = false)
    private long size;

    @Column(name = "content_type", length = 200)
    private String contentType;

    /** 完整文件 SHA-256；旧数据和非分片上传数据允许为空 */
    @Size(max = 64)
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @NotNull
    @Column(nullable = false)
    private boolean image;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 软删除时间（null 表示未删除，ddl-auto: update 自动补列） */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
