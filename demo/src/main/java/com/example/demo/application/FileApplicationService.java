package com.example.demo.application;

import com.example.demo.domain.FileKeyGenerator;
import com.example.demo.domain.model.FileEntity;
import com.example.demo.domain.repository.FileRepository;
import com.example.demo.interfaces.rest.exception.BizErrorCode;
import com.example.demo.interfaces.rest.exception.BizException;
import com.example.obs.core.ObsStorage;
import com.example.obs.core.ObsUploadRequest;
import com.example.obs.core.ObsUploadResult;
import com.example.obs.core.util.FileNameUtil;
import com.example.obs.core.util.FileTypeResolver;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件用例编排服务（应用层）。
 * 编排 key 生成、对象存储、元数据持久化，不包含业务规则以外的基础设施细节。
 * 业务层只依赖 obs-core 的 ObsStorage 接口，不感知七牛/阿里云实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileApplicationService {

    private final ObsStorage obsStorage;
    private final FileKeyGenerator fileKeyGenerator;
    private final FileRepository fileRepository;
    private final FileUploadPolicy uploadPolicy;
    private final ObsStorageCleaner obsStorageCleaner;

    /**
     * 上传文件：校验 → 生成 key → 存对象存储 → 持久化元数据。
     * 若对象存储成功但 DB 失败，回滚对象存储删除（补偿事务）。
     */
    @Transactional
    public UploadResult upload(MultipartFile file) {
        uploadPolicy.validate(file, UploadProfile.GENERAL);
        String originalName = FileNameUtil.basename(file.getOriginalFilename());
        String key = fileKeyGenerator.generate(originalName);
        String contentType = file.getContentType();
        boolean isImage = FileTypeResolver.isImage(contentType, originalName);

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BizException(BizErrorCode.QINIU_ERROR, e);
        }

        ObsUploadResult obsResult = obsStorage.upload(ObsUploadRequest.builder()
                .data(data)
                .key(key)
                .contentType(contentType)
                .originalName(originalName)
                .size(file.getSize())
                .build());

        // DB 失败则回滚对象存储
        try {
            FileEntity entity = FileEntity.builder()
                    .storageKey(obsResult.key())
                    .name(originalName)
                    .size(file.getSize())
                    .contentType(contentType)
                    .image(isImage)
                    .createdAt(LocalDateTime.now())
                    .build();
            fileRepository.save(entity);
        } catch (Exception dbEx) {
            log.error("元数据持久化失败，回滚对象存储: key={}", key, dbEx);
            obsStorageCleaner.deleteQuietly(key);
            throw new BizException(BizErrorCode.QINIU_ERROR, "元数据持久化失败");
        }

        return UploadResult.builder()
                .key(obsResult.key())
                .name(originalName)
                .url("/api/files/" + obsResult.key())
                .size(file.getSize())
                .contentType(contentType)
                .image(isImage)
                .build();
    }

    /**
     * 删除文件（软删除）：打标 deleted_at，对象与行的物理清理由定时任务在保留期后执行。
     * 打标是单条 UPDATE，天然原子，无需对象存储参与，误删可在保留期内恢复。
     */
    @Transactional
    public void delete(String key) {
        validateKey(key);
        fileRepository.softDeleteByStorageKey(key, LocalDateTime.now());
    }

    /**
     * 列出文件元数据（从 DB 读取）。
     */
    @Transactional(readOnly = true)
    public List<FileEntity> list(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        return fileRepository.findAllOrderByCreatedAtDesc(safeLimit);
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BizException(BizErrorCode.KEY_INVALID);
        }
    }

    /**
     * 上传结果（应用层返回给接口层）。
     */
    @Data
    @Builder
    public static class UploadResult {
        private String key;
        private String name;
        private String url;
        private long size;
        private String contentType;
        private boolean image;
    }

}
