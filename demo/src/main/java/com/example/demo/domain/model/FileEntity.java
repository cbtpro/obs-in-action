package com.example.demo.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件领域实体。
 * 纯 Java 领域对象，不含 JPA / Spring 注解，由基础设施层持久化。
 * 领域层只表达业务概念与规则，不关心存储技术。
 */
@Data
@Builder
public class FileEntity {

    private Long id;
    /** 存储 key（七牛/阿里云对象名） */
    private String storageKey;
    /** 原始文件名 */
    private String name;
    /** 文件大小（字节） */
    private long size;
    /** Content-Type */
    private String contentType;
    /** 文件完整内容的 SHA-256 十六进制摘要，用于秒传查重 */
    private String contentHash;
    /** 是否图片 */
    private boolean image;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 软删除时间（null 表示未删除，非 null 为已删除待物理清理） */
    private LocalDateTime deletedAt;
}
