package com.example.demo.infrastructure.persistence.jpa;

import com.example.demo.domain.model.UploadSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 分片上传会话 Spring Data JPA Repository。
 */
public interface UploadSessionJpaRepository extends JpaRepository<UploadSessionRecord, Long> {

    Optional<UploadSessionRecord> findFirstByFingerprintAndStatusOrderByCreatedAtDesc(
            String fingerprint, UploadSessionStatus status);

    Optional<UploadSessionRecord> findByStorageKeyAndStatus(String storageKey, UploadSessionStatus status);

    /** 状态流转：仅从 from 状态迁移，避免并发下覆盖他人写入（幂等安全） */
    @Modifying
    @Query("UPDATE UploadSessionRecord s SET s.status = :to, s.updatedAt = :updatedAt "
            + "WHERE s.id = :id AND s.status = :from")
    int updateStatusFrom(@Param("id") Long id,
                         @Param("from") UploadSessionStatus from,
                         @Param("to") UploadSessionStatus to,
                         @Param("updatedAt") LocalDateTime updatedAt);

    List<UploadSessionRecord> findByCreatedAtBefore(LocalDateTime threshold);

    void deleteById(Long id);
}
