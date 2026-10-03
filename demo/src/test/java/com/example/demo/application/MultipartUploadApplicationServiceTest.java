package com.example.demo.application;

import com.example.demo.domain.FileKeyGenerator;
import com.example.demo.domain.model.FileEntity;
import com.example.demo.domain.model.UploadSession;
import com.example.demo.domain.model.UploadSessionStatus;
import com.example.demo.domain.repository.FileRepository;
import com.example.demo.domain.repository.UploadSessionRepository;
import com.example.demo.interfaces.rest.exception.BizErrorCode;
import com.example.demo.interfaces.rest.exception.BizException;
import com.example.obs.core.ObsException;
import com.example.obs.core.ObsMultipartStorage;
import com.example.obs.core.ObsPartInfo;
import com.example.obs.core.ObsStorage;
import com.example.obs.core.ObsDownloadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.security.MessageDigest;
import java.util.Base64;
import java.io.ByteArrayInputStream;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MultipartUploadApplicationService 单元测试。
 * 覆盖断点续传语义（指纹命中/云端失效重开）、分片校验、
 * complete 幂等与补偿回滚、abort 幂等。
 */
class MultipartUploadApplicationServiceTest {

    private static final int CHUNK_SIZE = 5 * 1024 * 1024;
    private static final String FINGERPRINT = "fp-demo";
    private static final byte[] CONTENT_BYTES = new byte[]{1, 2, 3, 4};
    private static final String CONTENT_HASH = sha256(CONTENT_BYTES);

    private ObsMultipartStorage multipartStorage;
    private ObsStorage obsStorage;
    private UploadSessionRepository sessionRepository;
    private FileRepository fileRepository;
    private ObsStorageCleaner obsStorageCleaner;
    private MultipartUploadApplicationService service;

    @BeforeEach
    void setUp() {
        multipartStorage = mock(ObsMultipartStorage.class);
        obsStorage = mock(ObsStorage.class);
        sessionRepository = mock(UploadSessionRepository.class);
        fileRepository = mock(FileRepository.class);
        obsStorageCleaner = mock(ObsStorageCleaner.class);
        FileKeyGenerator keyGenerator = name -> "2026/09/" + name;
        service = new MultipartUploadApplicationService(
                multipartStorage, obsStorage, keyGenerator, sessionRepository, fileRepository,
                new FileUploadPolicy(), obsStorageCleaner);

        when(multipartStorage.chunkSize()).thenReturn(CHUNK_SIZE);
        when(obsStorage.download(anyString())).thenAnswer(inv ->
                new ObsDownloadResult(new ByteArrayInputStream(CONTENT_BYTES),
                        "application/octet-stream", CONTENT_BYTES.length));
        // save 打回 id，模拟数据库自增
        when(sessionRepository.save(any(UploadSession.class))).thenAnswer(inv -> {
            UploadSession s = inv.getArgument(0);
            s.setId(100L);
            return s;
        });
    }

    // ---------- init ----------

    @Test
    void initShouldCreateNewSessionWhenFingerprintMiss() {
        when(sessionRepository.findUploadingByFingerprint(FINGERPRINT)).thenReturn(Optional.empty());
        when(multipartStorage.initiate(anyString(), eq("application/zip"))).thenReturn("upload-1");

        var result = service.init(new MultipartUploadApplicationService.InitCommand(
                "backup.zip", 10_000_000L, "application/zip", FINGERPRINT, CONTENT_HASH));

        assertFalse(result.isResumed());
        assertEquals(100L, result.getSessionId());
        assertEquals(CHUNK_SIZE, result.getChunkSize());
        assertTrue(result.getUploadedParts().isEmpty());

        ArgumentCaptor<UploadSession> captor = ArgumentCaptor.forClass(UploadSession.class);
        verify(sessionRepository).save(captor.capture());
        UploadSession saved = captor.getValue();
        assertEquals(FINGERPRINT, saved.getFingerprint());
        assertEquals(CONTENT_HASH, saved.getContentHash());
        assertEquals("2026/09/backup.zip", saved.getStorageKey());
        assertEquals("upload-1", saved.getUploadId());
        assertEquals("backup.zip", saved.getName());
        assertEquals(UploadSessionStatus.UPLOADING, saved.getStatus());
    }

    @Test
    void initShouldInstantUploadWhenContentHashMatchesExistingFile() {
        FileEntity existing = FileEntity.builder()
                .storageKey("existing/key.zip")
                .name("existing.zip")
                .size(10_000_000L)
                .contentType("application/zip")
                .contentHash(CONTENT_HASH)
                .build();
        when(fileRepository.findByContentHashAndSize(CONTENT_HASH, 10_000_000L))
                .thenReturn(Optional.of(existing));

        var result = service.init(new MultipartUploadApplicationService.InitCommand(
                "backup.zip", 10_000_000L, "application/zip", FINGERPRINT, CONTENT_HASH));

        assertTrue(result.isInstantUpload());
        assertEquals("existing/key.zip", result.getFile().getKey());
        assertTrue(result.getUploadedParts().isEmpty());
        verify(sessionRepository, never()).findUploadingByFingerprint(anyString());
        verify(multipartStorage, never()).initiate(anyString(), anyString());
    }

    @Test
    void initShouldRejectInvalidContentHash() {
        var command = new MultipartUploadApplicationService.InitCommand(
                "backup.zip", 10_000_000L, "application/zip", FINGERPRINT, "not-sha256");

        BizException ex = assertThrows(BizException.class, () -> service.init(command));

        assertEquals(BizErrorCode.CONTENT_HASH_INVALID, ex.getErrorCode());
        verify(fileRepository, never()).findByContentHashAndSize(anyString(), anyLong());
    }

    @Test
    void initShouldResumeWhenCloudSessionAlive() {
        UploadSession existing = uploadingSession();
        when(sessionRepository.findUploadingByFingerprint(FINGERPRINT)).thenReturn(Optional.of(existing));
        List<ObsPartInfo> confirmed = List.of(new ObsPartInfo(1, "etag-1", CHUNK_SIZE));
        when(multipartStorage.listParts(existing.getStorageKey(), existing.getUploadId()))
                .thenReturn(confirmed);

        var result = service.init(new MultipartUploadApplicationService.InitCommand(
                "backup.zip", 10_000_000L, "application/zip", FINGERPRINT, CONTENT_HASH));

        assertTrue(result.isResumed());
        assertEquals(7L, result.getSessionId());
        assertEquals(confirmed, result.getUploadedParts());
        // 云端会话仍有效：不发起、不落新行
        verify(multipartStorage, never()).initiate(anyString(), anyString());
        verify(sessionRepository, never()).save(any(UploadSession.class));
    }

    @Test
    void initShouldStartOverWhenCloudSessionExpired() {
        UploadSession stale = uploadingSession();
        when(sessionRepository.findUploadingByFingerprint(FINGERPRINT)).thenReturn(Optional.of(stale));
        when(multipartStorage.listParts(stale.getStorageKey(), stale.getUploadId()))
                .thenThrow(new ObsException("session expired"));
        when(multipartStorage.initiate(anyString(), eq("application/zip"))).thenReturn("upload-2");

        var result = service.init(new MultipartUploadApplicationService.InitCommand(
                "backup.zip", 10_000_000L, "application/zip", FINGERPRINT, CONTENT_HASH));

        // 旧会话作废 + 重新发起
        verify(sessionRepository).updateStatus(eq(7L), eq(UploadSessionStatus.ABORTED), any());
        assertFalse(result.isResumed());
        assertEquals(100L, result.getSessionId());
    }

    @Test
    void initShouldRejectNonWhitelistedType() {
        var command = new MultipartUploadApplicationService.InitCommand(
                "movie.mp4", 10_000_000L, "video/mp4", FINGERPRINT, CONTENT_HASH);

        BizException ex = assertThrows(BizException.class, () -> service.init(command));
        assertEquals(BizErrorCode.TYPE_NOT_ALLOWED, ex.getErrorCode());
        verify(multipartStorage, never()).initiate(anyString(), anyString());
    }

    @Test
    void initShouldRejectOversizedFile() {
        var command = new MultipartUploadApplicationService.InitCommand(
                "backup.zip", 60 * 1024 * 1024L, "application/zip", FINGERPRINT, CONTENT_HASH);

        BizException ex = assertThrows(BizException.class, () -> service.init(command));
        assertEquals(BizErrorCode.SIZE_EXCEEDED, ex.getErrorCode());
    }

    // ---------- uploadPart ----------

    @Test
    void uploadPartShouldReturnEtag() {
        UploadSession session = uploadingSession();
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
        byte[] data = new byte[1024];
        String contentMd5 = md5(data);
        when(multipartStorage.uploadPart(
                session.getStorageKey(), session.getUploadId(), 1, data, contentMd5))
                .thenReturn("etag-1");

        assertEquals("etag-1", service.uploadPart(7L, 1, data, contentMd5));
    }

    @Test
    void uploadPartShouldRejectMismatchedContentMd5() {
        UploadSession session = uploadingSession();
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));

        BizException ex = assertThrows(BizException.class,
                () -> service.uploadPart(7L, 1, new byte[]{1, 2, 3}, "AAAAAAAAAAAAAAAAAAAAAA=="));

        assertEquals(BizErrorCode.PART_MD5_INVALID, ex.getErrorCode());
        verify(multipartStorage, never()).uploadPart(anyString(), anyString(), anyInt(), any(), anyString());
    }

    @Test
    void uploadPartShouldRejectInvalidPartNumber() {
        assertThrows(BizException.class, () -> service.uploadPart(7L, 0, new byte[10], "invalid"));
        assertThrows(BizException.class, () -> service.uploadPart(7L, 10001, new byte[10], "invalid"));
        verify(sessionRepository, never()).findById(anyLong());
    }

    @Test
    void uploadPartShouldRejectInvalidData() {
        UploadSession session = uploadingSession();
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));

        // null / 空 / 超过单片大小
        assertThrows(BizException.class, () -> service.uploadPart(7L, 1, null, "invalid"));
        assertThrows(BizException.class, () -> service.uploadPart(7L, 1, new byte[0], "invalid"));
        assertThrows(BizException.class,
                () -> service.uploadPart(7L, 1, new byte[CHUNK_SIZE + 1], "invalid"));
    }

    @Test
    void uploadPartShouldRejectNonUploadingSession() {
        UploadSession aborted = uploadingSession();
        aborted.setStatus(UploadSessionStatus.ABORTED);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(aborted));

        BizException ex = assertThrows(BizException.class,
                () -> service.uploadPart(7L, 1, new byte[10], "invalid"));
        assertEquals(BizErrorCode.SESSION_NOT_UPLOADING, ex.getErrorCode());
    }

    // ---------- complete ----------

    @Test
    void completeShouldPersistMetadataAndMarkCompleted() {
        UploadSession session = uploadingSession();
        session.setSize(300);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
        when(multipartStorage.listParts(session.getStorageKey(), session.getUploadId()))
                .thenReturn(List.of(new ObsPartInfo(1, "etag-1", 100), new ObsPartInfo(2, "etag-2", 200)));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.complete(7L);

        assertEquals(session.getStorageKey(), result.getKey());
        assertEquals("backup.zip", result.getName());
        assertEquals(300L, result.getSize());

        ArgumentCaptor<FileEntity> fileCaptor = ArgumentCaptor.forClass(FileEntity.class);
        verify(fileRepository).save(fileCaptor.capture());
        assertEquals("backup.zip", fileCaptor.getValue().getName());
        assertEquals(300L, fileCaptor.getValue().getSize());
        assertEquals(CONTENT_HASH, fileCaptor.getValue().getContentHash());
        assertTrue(fileCaptor.getValue().isImage());

        verify(multipartStorage).complete(eq(session.getStorageKey()), eq(session.getUploadId()),
                any(), eq(session.getContentType()));
        verify(sessionRepository).updateStatus(eq(7L), eq(UploadSessionStatus.COMPLETED), any());
    }

    @Test
    void completeShouldBeIdempotentForCompletedSession() {
        UploadSession session = uploadingSession();
        session.setStatus(UploadSessionStatus.COMPLETED);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
        when(fileRepository.findByStorageKey(session.getStorageKey()))
                .thenReturn(Optional.of(FileEntity.builder()
                        .storageKey(session.getStorageKey())
                        .name("backup.zip")
                        .size(300L)
                        .contentType("application/zip")
                        .image(false)
                        .build()));

        var result = service.complete(7L);

        assertEquals(session.getStorageKey(), result.getKey());
        assertEquals(300L, result.getSize());
        // 已完成会话：不触碰云端、不重复落库
        verify(multipartStorage, never()).listParts(anyString(), anyString());
        verify(multipartStorage, never()).complete(anyString(), anyString(), any(), anyString());
        verify(fileRepository, never()).save(any(FileEntity.class));
    }

    @Test
    void completeShouldRejectEmptyParts() {
        UploadSession session = uploadingSession();
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
        when(multipartStorage.listParts(session.getStorageKey(), session.getUploadId()))
                .thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () -> service.complete(7L));
        assertEquals(BizErrorCode.FILE_EMPTY, ex.getErrorCode());
        verify(multipartStorage, never()).complete(anyString(), anyString(), any(), anyString());
    }

    @Test
    void completeShouldRejectSizeMismatch() {
        UploadSession session = uploadingSession();
        session.setSize(999);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
        when(multipartStorage.listParts(session.getStorageKey(), session.getUploadId()))
                .thenReturn(List.of(new ObsPartInfo(1, "etag-1", 100)));

        BizException ex = assertThrows(BizException.class, () -> service.complete(7L));
        assertEquals(BizErrorCode.SIZE_MISMATCH, ex.getErrorCode());
        verify(multipartStorage, never()).complete(anyString(), anyString(), any(), anyString());
        verify(fileRepository, never()).save(any(FileEntity.class));
    }

    @Test
    void completeShouldDeleteObjectWhenContentHashMismatches() {
        UploadSession session = uploadingSession();
        session.setSize(300);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
        when(multipartStorage.listParts(session.getStorageKey(), session.getUploadId()))
                .thenReturn(List.of(new ObsPartInfo(1, "etag-1", 300)));
        when(obsStorage.download(session.getStorageKey())).thenReturn(
                new ObsDownloadResult(new ByteArrayInputStream(new byte[]{9, 9, 9}),
                        "application/octet-stream", 3));

        BizException ex = assertThrows(BizException.class, () -> service.complete(7L));

        assertEquals(BizErrorCode.CONTENT_HASH_MISMATCH, ex.getErrorCode());
        verify(obsStorageCleaner).deleteQuietly(session.getStorageKey());
        verify(fileRepository, never()).save(any(FileEntity.class));
        verify(sessionRepository, never()).updateStatus(eq(7L), eq(UploadSessionStatus.COMPLETED), any());
    }

    @Test
    void completeShouldSucceedWithoutDedupeIndexWhenHashVerificationIsUnavailable() {
        UploadSession session = uploadingSession();
        session.setSize(300);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
        when(multipartStorage.listParts(session.getStorageKey(), session.getUploadId()))
                .thenReturn(List.of(new ObsPartInfo(1, "etag-1", 300)));
        when(obsStorage.download(session.getStorageKey())).thenThrow(new RuntimeException("TLS mismatch"));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.complete(7L);

        assertEquals(session.getStorageKey(), result.getKey());
        ArgumentCaptor<FileEntity> captor = ArgumentCaptor.forClass(FileEntity.class);
        verify(fileRepository).save(captor.capture());
        assertEquals(null, captor.getValue().getContentHash());
        verify(obsStorageCleaner, never()).deleteQuietly(session.getStorageKey());
        verify(sessionRepository).updateStatus(eq(7L), eq(UploadSessionStatus.COMPLETED), any());
    }

    @Test
    void completeShouldRollbackObjectWhenDbFails() {
        UploadSession session = uploadingSession();
        session.setSize(300);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));
        when(multipartStorage.listParts(session.getStorageKey(), session.getUploadId()))
                .thenReturn(List.of(new ObsPartInfo(1, "etag-1", 300)));
        doThrow(new RuntimeException("db down")).when(fileRepository).save(any(FileEntity.class));

        BizException ex = assertThrows(BizException.class, () -> service.complete(7L));
        // 云端对象已拼接但元数据落库失败：补偿删除，会话保持 UPLOADING 供重试
        verify(obsStorageCleaner).deleteQuietly(session.getStorageKey());
        verify(sessionRepository, never()).updateStatus(eq(7L), eq(UploadSessionStatus.COMPLETED), any());
    }

    @Test
    void completeShouldRejectMissingSession() {
        when(sessionRepository.findById(7L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.complete(7L));
        assertEquals(BizErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void completeShouldRejectAbortedSession() {
        UploadSession session = uploadingSession();
        session.setStatus(UploadSessionStatus.ABORTED);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));

        BizException ex = assertThrows(BizException.class, () -> service.complete(7L));
        assertEquals(BizErrorCode.SESSION_NOT_UPLOADING, ex.getErrorCode());
    }

    // ---------- abort ----------

    @Test
    void abortShouldAbortUploadingSession() {
        UploadSession session = uploadingSession();
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(session));

        service.abort(7L);

        verify(multipartStorage).abort(session.getStorageKey(), session.getUploadId());
        verify(sessionRepository).updateStatus(eq(7L), eq(UploadSessionStatus.ABORTED), any());
    }

    @Test
    void abortShouldBeNoOpForNonUploadingSession() {
        UploadSession completed = uploadingSession();
        completed.setStatus(UploadSessionStatus.COMPLETED);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(completed));

        service.abort(7L);

        verify(multipartStorage, never()).abort(anyString(), anyString());
        verify(sessionRepository, never()).updateStatus(anyLong(), any(), any());
    }

    private UploadSession uploadingSession() {
        return UploadSession.builder()
                .id(7L)
                .fingerprint(FINGERPRINT)
                .contentHash(CONTENT_HASH)
                .storageKey("2026/09/backup.zip")
                .uploadId("upload-1")
                .name("backup.zip")
                .size(10_000_000L)
                .contentType("application/zip")
                .image(true)
                .chunkSize(CHUNK_SIZE)
                .status(UploadSessionStatus.UPLOADING)
                .build();
    }

    private String md5(byte[] data) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
