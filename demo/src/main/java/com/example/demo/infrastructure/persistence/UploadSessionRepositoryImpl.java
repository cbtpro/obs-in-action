package com.example.demo.infrastructure.persistence;

import com.example.demo.domain.model.UploadSession;
import com.example.demo.domain.model.UploadSessionStatus;
import com.example.demo.domain.repository.UploadSessionRepository;
import com.example.demo.infrastructure.persistence.jpa.UploadSessionJpaRepository;
import com.example.demo.infrastructure.persistence.jpa.UploadSessionRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 分片上传会话领域仓储的 JPA 实现。
 * 负责 UploadSession ↔ UploadSessionRecord 的双向转换，对应用层暴露领域接口。
 */
@Repository
@RequiredArgsConstructor
public class UploadSessionRepositoryImpl implements UploadSessionRepository {

    private final UploadSessionJpaRepository jpaRepository;

    @Override
    public UploadSession save(UploadSession session) {
        UploadSessionRecord saved = jpaRepository.save(toRecord(session));
        return toEntity(saved);
    }

    @Override
    public Optional<UploadSession> findById(Long id) {
        return jpaRepository.findById(id).map(this::toEntity);
    }

    @Override
    public Optional<UploadSession> findUploadingByFingerprint(String fingerprint) {
        return jpaRepository.findFirstByFingerprintAndStatusOrderByCreatedAtDesc(
                        fingerprint, UploadSessionStatus.UPLOADING)
                .map(this::toEntity);
    }

    @Override
    public Optional<UploadSession> findCompletedByStorageKey(String storageKey) {
        return jpaRepository.findByStorageKeyAndStatus(storageKey, UploadSessionStatus.COMPLETED)
                .map(this::toEntity);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, UploadSessionStatus status, LocalDateTime updatedAt) {
        // 从当前任意非目标状态迁移的简化写法：仅在仍为 UPLOADING 时允许终态流转
        jpaRepository.updateStatusFrom(id, UploadSessionStatus.UPLOADING, status, updatedAt);
    }

    @Override
    public List<UploadSession> findBefore(LocalDateTime threshold) {
        return jpaRepository.findByCreatedAtBefore(threshold)
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    @Transactional
    public void hardDeleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    private UploadSessionRecord toRecord(UploadSession s) {
        UploadSessionRecord r = new UploadSessionRecord();
        r.setId(s.getId());
        r.setFingerprint(s.getFingerprint());
        r.setContentHash(s.getContentHash());
        r.setStorageKey(s.getStorageKey());
        r.setUploadId(s.getUploadId());
        r.setName(s.getName());
        r.setSize(s.getSize());
        r.setContentType(s.getContentType());
        r.setImage(s.isImage());
        r.setChunkSize(s.getChunkSize());
        r.setStatus(s.getStatus());
        r.setCreatedAt(s.getCreatedAt());
        r.setUpdatedAt(s.getUpdatedAt());
        return r;
    }

    private UploadSession toEntity(UploadSessionRecord r) {
        return UploadSession.builder()
                .id(r.getId())
                .fingerprint(r.getFingerprint())
                .contentHash(r.getContentHash())
                .storageKey(r.getStorageKey())
                .uploadId(r.getUploadId())
                .name(r.getName())
                .size(r.getSize())
                .contentType(r.getContentType())
                .image(r.isImage())
                .chunkSize(r.getChunkSize())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
