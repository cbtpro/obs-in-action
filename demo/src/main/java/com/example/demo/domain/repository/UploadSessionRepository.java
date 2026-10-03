package com.example.demo.domain.repository;

import com.example.demo.domain.model.UploadSession;
import com.example.demo.domain.model.UploadSessionStatus;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 分片上传会话领域仓储接口。
 * 由基础设施层实现（JPA）；应用层通过它完成会话的查找与状态流转。
 */
public interface UploadSessionRepository {

    UploadSession save(UploadSession session);

    Optional<UploadSession> findById(Long id);

    /** 按指纹找最新未完成会话（断点续传入口；同指纹可有多个历史会话） */
    Optional<UploadSession> findUploadingByFingerprint(String fingerprint);

    /** 按存储 key 找已完成会话（complete 幂等核对） */
    Optional<UploadSession> findCompletedByStorageKey(String storageKey);

    /** 更新状态（幂等：非目标状态不覆盖） */
    void updateStatus(Long id, UploadSessionStatus status, LocalDateTime updatedAt);

    /** 创建时间早于阈值的会话（清理任务扫描，含全部状态） */
    java.util.List<UploadSession> findBefore(LocalDateTime threshold);

    /** 物理删除行（清理任务使用） */
    void hardDeleteById(Long id);
}
