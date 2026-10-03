package com.example.demo.infrastructure.persistence;

import com.example.demo.domain.model.FileEntity;
import com.example.demo.domain.repository.FileRepository;
import com.example.demo.infrastructure.persistence.jpa.FileRecord;
import com.example.demo.infrastructure.persistence.jpa.FileRecordJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 领域仓储的 JPA 实现。
 * 负责 FileEntity ↔ FileRecord 的双向转换，对应用层暴露领域接口。
 */
@Repository
@RequiredArgsConstructor
public class FileRepositoryImpl implements FileRepository {

    private final FileRecordJpaRepository jpaRepository;

    @Override
    public FileEntity save(FileEntity entity) {
        FileRecord record = toRecord(entity);
        FileRecord saved = jpaRepository.save(record);
        return toEntity(saved);
    }

    @Override
    public Optional<FileEntity> findByStorageKey(String storageKey) {
        return jpaRepository.findByStorageKeyAndDeletedAtIsNull(storageKey).map(this::toEntity);
    }

    @Override
    public Optional<FileEntity> findByContentHashAndSize(String contentHash, long size) {
        return jpaRepository
                .findFirstByContentHashAndSizeAndDeletedAtIsNullOrderByCreatedAtDesc(contentHash, size)
                .map(this::toEntity);
    }

    @Override
    public List<FileEntity> findAllOrderByCreatedAtDesc(int limit) {
        return jpaRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc(PageRequest.of(0, limit))
                .stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void softDeleteByStorageKey(String storageKey, LocalDateTime deletedAt) {
        jpaRepository.softDeleteByStorageKey(storageKey, deletedAt);
    }

    @Override
    public List<FileEntity> findDeletedBefore(LocalDateTime threshold) {
        return jpaRepository.findByDeletedAtBefore(threshold)
                .stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void hardDeleteByStorageKey(String storageKey) {
        jpaRepository.deleteByStorageKey(storageKey);
    }

    private FileRecord toRecord(FileEntity e) {
        FileRecord r = new FileRecord();
        r.setId(e.getId());
        r.setStorageKey(e.getStorageKey());
        r.setName(e.getName());
        r.setSize(e.getSize());
        r.setContentType(e.getContentType());
        r.setContentHash(e.getContentHash());
        r.setImage(e.isImage());
        r.setCreatedAt(e.getCreatedAt());
        r.setDeletedAt(e.getDeletedAt());
        return r;
    }

    private FileEntity toEntity(FileRecord r) {
        return FileEntity.builder()
                .id(r.getId())
                .storageKey(r.getStorageKey())
                .name(r.getName())
                .size(r.getSize())
                .contentType(r.getContentType())
                .contentHash(r.getContentHash())
                .image(r.isImage())
                .createdAt(r.getCreatedAt())
                .deletedAt(r.getDeletedAt())
                .build();
    }
}
