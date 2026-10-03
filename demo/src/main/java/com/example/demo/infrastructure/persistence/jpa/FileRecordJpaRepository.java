package com.example.demo.infrastructure.persistence.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文件元数据 Spring Data JPA Repository。
 * 常规查询按 deleted_at IS NULL 过滤；软删打标与物理删除用专用方法。
 */
public interface FileRecordJpaRepository extends JpaRepository<FileRecord, Long> {

    Optional<FileRecord> findByStorageKeyAndDeletedAtIsNull(String storageKey);

    Optional<FileRecord> findFirstByContentHashAndSizeAndDeletedAtIsNullOrderByCreatedAtDesc(
            String contentHash, long size);

    List<FileRecord> findAllByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    /** 软删除打标：单条 UPDATE，幂等（已删除的不再刷新时间） */
    @Modifying
    @Query("UPDATE FileRecord f SET f.deletedAt = :deletedAt, f.contentHash = null "
            + "WHERE f.storageKey = :storageKey AND f.deletedAt IS NULL")
    void softDeleteByStorageKey(@Param("storageKey") String storageKey, @Param("deletedAt") LocalDateTime deletedAt);

    /** 软删除时间早于阈值的记录（NULL 不参与比较，天然被排除） */
    List<FileRecord> findByDeletedAtBefore(LocalDateTime threshold);

    void deleteByStorageKey(String storageKey);
}
