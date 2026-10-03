package com.example.demo.application;

import com.example.demo.domain.model.UploadSession;
import com.example.demo.domain.model.UploadSessionStatus;
import com.example.demo.domain.repository.UploadSessionRepository;
import com.example.obs.core.ObsException;
import com.example.obs.core.ObsMultipartStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MultipartSessionCleanupService 单元测试。
 * 覆盖孤儿会话清理语义：UPLOADING 先云端 abort 再删行、abort 失败保留重试、
 * 已完成/已作废会话直接删行、空数据空转。
 */
class MultipartSessionCleanupServiceTest {

    private UploadSessionRepository sessionRepository;
    private ObsMultipartStorage multipartStorage;
    private MultipartSessionCleanupService service;

    private final LocalDateTime threshold = LocalDateTime.of(2026, 1, 1, 0, 0);

    @BeforeEach
    void setUp() {
        sessionRepository = mock(UploadSessionRepository.class);
        multipartStorage = mock(ObsMultipartStorage.class);
        service = new MultipartSessionCleanupService(sessionRepository, multipartStorage);
    }

    @Test
    void shouldAbortUploadingSessionThenDeleteRow() {
        UploadSession orphan = session(UploadSessionStatus.UPLOADING, 9L, "2026/09/big.zip");
        when(sessionRepository.findBefore(threshold)).thenReturn(List.of(orphan));

        int purged = service.purgeExpired(threshold);

        assertEquals(1, purged);
        // 顺序：先云端 abort（释放已传分片），再物理删行
        verify(multipartStorage).abort(orphan.getStorageKey(), orphan.getUploadId());
        verify(sessionRepository).hardDeleteById(orphan.getId());
    }

    @Test
    void shouldKeepRowWhenAbortFails() {
        UploadSession orphan = session(UploadSessionStatus.UPLOADING, 9L, "2026/09/big.zip");
        when(sessionRepository.findBefore(threshold)).thenReturn(List.of(orphan));
        doThrow(new ObsException("abort down")).when(multipartStorage).abort(anyString(), anyString());

        int purged = service.purgeExpired(threshold);

        // 云端终止失败：行保留待下轮重试（abort 幂等，重复执行安全）
        assertEquals(0, purged);
        verify(sessionRepository, never()).hardDeleteById(anyLong());
    }

    @Test
    void shouldDeleteCompletedRowWithoutCloudCall() {
        UploadSession done = session(UploadSessionStatus.COMPLETED, 9L, "2026/09/big.zip");
        when(sessionRepository.findBefore(threshold)).thenReturn(List.of(done));

        int purged = service.purgeExpired(threshold);

        assertEquals(1, purged);
        // 已完成会话无云端资源：不 abort，直接删行
        verify(multipartStorage, never()).abort(anyString(), anyString());
        verify(sessionRepository).hardDeleteById(done.getId());
    }

    @Test
    void shouldDeleteAbortedRowWithoutCloudCall() {
        UploadSession aborted = session(UploadSessionStatus.ABORTED, 9L, "2026/09/big.zip");
        when(sessionRepository.findBefore(threshold)).thenReturn(List.of(aborted));

        int purged = service.purgeExpired(threshold);

        assertEquals(1, purged);
        verify(multipartStorage, never()).abort(anyString(), anyString());
        verify(sessionRepository).hardDeleteById(aborted.getId());
    }

    @Test
    void shouldHandleMixedSessionsIndependently() {
        UploadSession uploading = session(UploadSessionStatus.UPLOADING, 9L, "2026/09/big.zip");
        UploadSession completed = session(UploadSessionStatus.COMPLETED, 10L, "2026/09/old.zip");
        when(sessionRepository.findBefore(threshold)).thenReturn(List.of(uploading, completed));

        int purged = service.purgeExpired(threshold);

        // 各会话独立清理，互不影响
        assertEquals(2, purged);
        verify(multipartStorage).abort(uploading.getStorageKey(), uploading.getUploadId());
        verify(multipartStorage, never()).abort(completed.getStorageKey(), completed.getUploadId());
        verify(sessionRepository).hardDeleteById(9L);
        verify(sessionRepository).hardDeleteById(10L);
    }

    @Test
    void shouldReturnZeroWhenNothingToClean() {
        when(sessionRepository.findBefore(threshold)).thenReturn(List.of());

        assertEquals(0, service.purgeExpired(threshold));
        verify(sessionRepository, never()).hardDeleteById(anyLong());
    }

    private UploadSession session(UploadSessionStatus status, long id, String storageKey) {
        return UploadSession.builder()
                .id(id)
                .fingerprint("fp-" + id)
                .storageKey(storageKey)
                .uploadId("upload-" + id)
                .name("big.zip")
                .size(1024L)
                .contentType("application/zip")
                .chunkSize(5 * 1024 * 1024)
                .status(status)
                .build();
    }
}
