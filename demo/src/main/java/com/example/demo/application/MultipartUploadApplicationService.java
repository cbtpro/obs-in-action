package com.example.demo.application;

import com.example.demo.domain.FileKeyGenerator;
import com.example.demo.domain.model.FileEntity;
import com.example.demo.domain.model.UploadSession;
import com.example.demo.domain.model.UploadSessionStatus;
import com.example.demo.domain.repository.FileRepository;
import com.example.demo.domain.repository.UploadSessionRepository;
import com.example.demo.interfaces.rest.exception.BizErrorCode;
import com.example.demo.interfaces.rest.exception.BizException;
import com.example.obs.core.ObsMultipartStorage;
import com.example.obs.core.ObsPartInfo;
import com.example.obs.core.ObsStorage;
import com.example.obs.core.util.FileNameUtil;
import com.example.obs.core.util.FileTypeResolver;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 分片上传用例编排服务（应用层）。
 * 工作流：init（指纹找回/新发起）→ uploadPart 逐片透传 → complete 云端拼接 + 元数据落库。
 * 断点续传的唯一权威是云端 listParts：本地会话只承担"找到 uploadId"的索引职责，
 * 已确认分片一律以云端返回为准，重试、续传、并发重复上传同一分片均安全。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadApplicationService {

    /** 分片序号上限（七牛/阿里云共同约束） */
    private static final int MAX_PART_NUMBER = 10000;
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final ObsMultipartStorage multipartStorage;
    private final ObsStorage obsStorage;
    private final FileKeyGenerator fileKeyGenerator;
    private final UploadSessionRepository sessionRepository;
    private final FileRepository fileRepository;
    private final FileUploadPolicy uploadPolicy;
    private final ObsStorageCleaner obsStorageCleaner;

    /**
     * 发起（或续传）分片上传会话。
     * 指纹命中 UPLOADING 会话时查询云端已确认分片返回（客户端据此跳过已传分片）；
     * 云端会话已失效（如七牛 7 天过期）则作废旧会话重新发起。
     */
    @Transactional
    public InitResult init(InitCommand command) {
        uploadPolicy.validate(command.contentType(), command.size(), UploadProfile.GENERAL);
        String originalName = FileNameUtil.basename(command.name());
        boolean isImage = FileTypeResolver.isImage(command.contentType(), command.name());
        String contentHash = normalizeContentHash(command.contentHash());

        java.util.Optional<FileEntity> existing = fileRepository.findByContentHashAndSize(contentHash, command.size());
        if (existing.isPresent()) {
            return InitResult.builder()
                    .instantUpload(true)
                    .resumed(false)
                    .uploadedParts(List.of())
                    .file(toCompleteResult(existing.get()))
                    .build();
        }

        return sessionRepository.findUploadingByFingerprint(command.fingerprint())
                .flatMap(session -> tryResume(session, contentHash))
                .orElseGet(() -> createSession(command, originalName, isImage, contentHash));
    }

    /**
     * 上传单个分片：透传到云端，返回 etag。
     * 同一分片号重复上传云端直接覆盖（重试安全），本服务不落分片状态。
     */
    public String uploadPart(long sessionId, int partNumber, byte[] data, String contentMd5) {
        if (partNumber < 1 || partNumber > MAX_PART_NUMBER) {
            throw new BizException(BizErrorCode.PART_NUMBER_INVALID);
        }
        UploadSession session = requireUploading(sessionId);
        if (data == null || data.length == 0 || data.length > session.getChunkSize()) {
            throw new BizException(BizErrorCode.PART_DATA_INVALID);
        }
        validateContentMd5(data, contentMd5);
        return multipartStorage.uploadPart(
                session.getStorageKey(), session.getUploadId(), partNumber, data, contentMd5);
    }

    /**
     * 完成分片上传：以云端已确认分片为准拼接对象，校验总量后落文件元数据。
     * 幂等：已完成会话直接返回既有文件信息（网络重试导致 complete 重复到达）。
     * 云端 complete 成功但 DB 失败时回滚删除对象（补偿事务），会话保持 UPLOADING 由客户端重试。
     */
    @Transactional
    public CompleteResult complete(long sessionId) {
        UploadSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BizException(BizErrorCode.NOT_FOUND));
        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            FileEntity file = fileRepository.findByStorageKey(session.getStorageKey())
                    .orElseThrow(() -> new BizException(BizErrorCode.NOT_FOUND));
            return toCompleteResult(file);
        }
        if (session.getStatus() != UploadSessionStatus.UPLOADING) {
            throw new BizException(BizErrorCode.SESSION_NOT_UPLOADING);
        }

        List<ObsPartInfo> parts = multipartStorage.listParts(session.getStorageKey(), session.getUploadId());
        if (parts.isEmpty()) {
            throw new BizException(BizErrorCode.FILE_EMPTY);
        }
        long totalSize = parts.stream().mapToLong(ObsPartInfo::size).sum();
        if (totalSize != session.getSize()) {
            throw new BizException(BizErrorCode.SIZE_MISMATCH,
                    "云端分片总量 " + totalSize + " 与声明大小 " + session.getSize() + " 不一致");
        }

        multipartStorage.complete(session.getStorageKey(), session.getUploadId(), parts, session.getContentType());
        boolean contentHashVerified = verifyCompletedContentHash(session);
        LocalDateTime completedAt = LocalDateTime.now();
        try {
            FileEntity entity = FileEntity.builder()
                    .storageKey(session.getStorageKey())
                    .name(session.getName())
                    .size(session.getSize())
                    .contentType(session.getContentType())
                    // 下载域名暂时不可用时上传仍成功，但不把未复核哈希放入秒传索引。
                    .contentHash(contentHashVerified ? session.getContentHash() : null)
                    .image(session.isImage())
                    .createdAt(completedAt)
                    .build();
            fileRepository.save(entity);
            sessionRepository.updateStatus(sessionId, UploadSessionStatus.COMPLETED, completedAt);
        } catch (Exception dbEx) {
            log.error("分片上传元数据持久化失败，回滚对象存储: key={}", session.getStorageKey(), dbEx);
            obsStorageCleaner.deleteQuietly(session.getStorageKey());
            throw new BizException(BizErrorCode.QINIU_ERROR, "元数据持久化失败");
        }
        return toCompleteResult(session);
    }

    /**
     * 终止会话：云端 abort（幂等，会话不存在静默成功）+ 会话标记作废。
     * 已完成/已作废的会话终止是幂等 no-op。
     */
    @Transactional
    public void abort(long sessionId) {
        UploadSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BizException(BizErrorCode.NOT_FOUND));
        if (session.getStatus() != UploadSessionStatus.UPLOADING) {
            return;
        }
        multipartStorage.abort(session.getStorageKey(), session.getUploadId());
        sessionRepository.updateStatus(sessionId, UploadSessionStatus.ABORTED, LocalDateTime.now());
    }

    /**
     * 尝试续传：云端会话仍有效则返回已确认分片，失效则作废旧会话返回空。
     */
    private java.util.Optional<InitResult> tryResume(UploadSession session, String contentHash) {
        if (!contentHash.equals(session.getContentHash())) {
            return java.util.Optional.empty();
        }
        try {
            List<ObsPartInfo> parts = multipartStorage.listParts(session.getStorageKey(), session.getUploadId());
            return java.util.Optional.of(InitResult.builder()
                    .sessionId(session.getId())
                    .chunkSize(session.getChunkSize())
                    .resumed(true)
                    .instantUpload(false)
                    .uploadedParts(parts)
                    .build());
        } catch (Exception e) {
            // 云端会话已失效（过期/已完成/已终止）：作废本地会话，走新发起
            log.warn("分片会话续传检查失败，作废旧会话重新发起: sessionId={} err={}", session.getId(), e.getMessage());
            sessionRepository.updateStatus(session.getId(), UploadSessionStatus.ABORTED, LocalDateTime.now());
            return java.util.Optional.empty();
        }
    }

    private InitResult createSession(
            InitCommand command, String originalName, boolean isImage, String contentHash) {
        String key = fileKeyGenerator.generate(originalName);
        String uploadId = multipartStorage.initiate(key, command.contentType());
        LocalDateTime now = LocalDateTime.now();
        UploadSession session = sessionRepository.save(UploadSession.builder()
                .fingerprint(command.fingerprint())
                .contentHash(contentHash)
                .storageKey(key)
                .uploadId(uploadId)
                .name(originalName)
                .size(command.size())
                .contentType(command.contentType())
                .image(isImage)
                .chunkSize(multipartStorage.chunkSize())
                .status(UploadSessionStatus.UPLOADING)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return InitResult.builder()
                .sessionId(session.getId())
                .chunkSize(session.getChunkSize())
                .resumed(false)
                .instantUpload(false)
                .uploadedParts(List.of())
                .build();
    }

    private String normalizeContentHash(String contentHash) {
        if (contentHash == null || !SHA256_HEX.matcher(contentHash).matches()) {
            throw new BizException(BizErrorCode.CONTENT_HASH_INVALID);
        }
        return contentHash.toLowerCase(Locale.ROOT);
    }

    private void validateContentMd5(byte[] data, String suppliedBase64Md5) {
        if (suppliedBase64Md5 == null || suppliedBase64Md5.isBlank()) {
            throw new BizException(BizErrorCode.PART_MD5_INVALID);
        }
        try {
            byte[] supplied = Base64.getDecoder().decode(suppliedBase64Md5);
            byte[] actual = MessageDigest.getInstance("MD5").digest(data);
            if (supplied.length != actual.length || !MessageDigest.isEqual(supplied, actual)) {
                throw new BizException(BizErrorCode.PART_MD5_INVALID);
            }
        } catch (IllegalArgumentException | NoSuchAlgorithmException e) {
            throw new BizException(BizErrorCode.PART_MD5_INVALID);
        }
    }

    private boolean verifyCompletedContentHash(UploadSession session) {
        try (java.io.InputStream stream = obsStorage.download(session.getStorageKey()).stream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equals(session.getContentHash())) {
                obsStorageCleaner.deleteQuietly(session.getStorageKey());
                throw new BizException(BizErrorCode.CONTENT_HASH_MISMATCH);
            }
            return true;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // 完整对象已由各分片 Content-MD5 和云端 complete 校验。下载域名、TLS 或网络
            // 暂时不可用不应把已成功的上传变成失败；此时不写 content_hash，禁用该对象的秒传。
            log.warn("分片文件 SHA-256 复核暂不可用，本次上传保留但不加入秒传索引: key={} err={}",
                    session.getStorageKey(), e.getMessage());
            return false;
        }
    }

    private UploadSession requireUploading(long sessionId) {
        UploadSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BizException(BizErrorCode.NOT_FOUND));
        if (session.getStatus() != UploadSessionStatus.UPLOADING) {
            throw new BizException(BizErrorCode.SESSION_NOT_UPLOADING);
        }
        return session;
    }

    private CompleteResult toCompleteResult(UploadSession session) {
        return CompleteResult.builder()
                .key(session.getStorageKey())
                .name(session.getName())
                .url("/api/files/" + session.getStorageKey())
                .size(session.getSize())
                .contentType(session.getContentType())
                .image(session.isImage())
                .build();
    }

    private CompleteResult toCompleteResult(FileEntity file) {
        return CompleteResult.builder()
                .key(file.getStorageKey())
                .name(file.getName())
                .url("/api/files/" + file.getStorageKey())
                .size(file.getSize())
                .contentType(file.getContentType())
                .image(file.isImage())
                .build();
    }

    /** init 入参 */
    public record InitCommand(
            String name, long size, String contentType, String fingerprint, String contentHash) {
    }

    /** init 出参：会话标识 + 单片大小 + 云端已确认分片（续传时非空） */
    @Data
    @Builder
    public static class InitResult {
        private Long sessionId;
        private int chunkSize;
        private boolean resumed;
        private boolean instantUpload;
        private CompleteResult file;
        private List<ObsPartInfo> uploadedParts;
    }

    /** complete 出参：与单文件上传同构的文件信息 */
    @Data
    @Builder
    public static class CompleteResult {
        private String key;
        private String name;
        private String url;
        private long size;
        private String contentType;
        private boolean image;
    }
}
