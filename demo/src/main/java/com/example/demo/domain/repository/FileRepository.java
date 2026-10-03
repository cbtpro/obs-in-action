package com.example.demo.domain.repository;

import com.example.demo.domain.model.FileEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文件领域仓储接口。
 * 定义领域层对持久化的需求，由基础设施层实现（JPA）。
 * 切换持久化技术（如 MyBatis）只需新增实现，领域层不变。
 * 查询方法一律过滤软删数据；软删除打标与物理清理由专用方法承接。
 */
public interface FileRepository {

    FileEntity save(FileEntity entity);

    /** 仅查未删除的文件 */
    Optional<FileEntity> findByStorageKey(String storageKey);

    /** 按完整内容 SHA-256 和大小查找未删除文件，用于秒传 */
    Optional<FileEntity> findByContentHashAndSize(String contentHash, long size);

    /** 仅列未删除的文件 */
    List<FileEntity> findAllOrderByCreatedAtDesc(int limit);

    /** 软删除打标（deleted_at） */
    void softDeleteByStorageKey(String storageKey, LocalDateTime deletedAt);

    /** 软删除时间早于阈值的记录（供物理清理任务扫描） */
    List<FileEntity> findDeletedBefore(LocalDateTime threshold);

    /** 物理删除行（仅清理任务使用，先删对象存储再调用） */
    void hardDeleteByStorageKey(String storageKey);
}
