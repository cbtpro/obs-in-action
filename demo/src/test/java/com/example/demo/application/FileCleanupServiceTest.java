package com.example.demo.application;

import com.example.demo.domain.model.Candidate;
import com.example.demo.domain.model.FileEntity;
import com.example.demo.domain.repository.CandidateRepository;
import com.example.demo.domain.repository.FileRepository;
import com.example.obs.core.ObsException;
import com.example.obs.core.ObsStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * FileCleanupService 单元测试。
 * 覆盖：到期文件/候选人物理清理顺序（先对象后行）、
 * 对象删除失败时记录保留待重试、无到期数据时的空转。
 */
class FileCleanupServiceTest {

    private FileRepository fileRepository;
    private CandidateRepository candidateRepository;
    private ObsStorage obsStorage;
    private FileCleanupService service;

    private final LocalDateTime threshold = LocalDateTime.of(2026, 1, 1, 0, 0);

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        candidateRepository = mock(CandidateRepository.class);
        obsStorage = mock(ObsStorage.class);
        service = new FileCleanupService(fileRepository, candidateRepository, obsStorage);
    }

    @Test
    void shouldPurgeExpiredFileThenHardDeleteRow() {
        when(fileRepository.findDeletedBefore(threshold)).thenReturn(List.of(
                FileEntity.builder().storageKey("k-file").name("a.png").build()));
        when(candidateRepository.findDeletedBefore(threshold)).thenReturn(List.of());

        int purged = service.purgeExpired(threshold);

        assertEquals(1, purged);
        // 顺序：先删对象存储，再硬删 DB 行
        InOrder inOrder = inOrder(obsStorage, fileRepository);
        inOrder.verify(obsStorage).delete("k-file");
        inOrder.verify(fileRepository).hardDeleteByStorageKey("k-file");
    }

    @Test
    void shouldPurgeExpiredCandidateWithBothObjects() {
        when(fileRepository.findDeletedBefore(threshold)).thenReturn(List.of());
        when(candidateRepository.findDeletedBefore(threshold)).thenReturn(List.of(
                Candidate.builder().id(9L).photoKey("k-photo").resumeKey("k-resume").build()));

        int purged = service.purgeExpired(threshold);

        assertEquals(1, purged);
        verify(obsStorage).delete("k-photo");
        verify(obsStorage).delete("k-resume");
        verify(candidateRepository).hardDeleteById(9L);
    }

    @Test
    void shouldKeepRowWhenObjectDeleteFails() {
        when(fileRepository.findDeletedBefore(threshold)).thenReturn(List.of(
                FileEntity.builder().storageKey("k-file").name("a.png").build()));
        when(candidateRepository.findDeletedBefore(threshold)).thenReturn(List.of());
        doThrow(new ObsException("delete down")).when(obsStorage).delete("k-file");

        int purged = service.purgeExpired(threshold);

        // 对象删除失败：行保留待下轮重试，不计入清理数
        assertEquals(0, purged);
        verify(fileRepository, never()).hardDeleteByStorageKey(anyString());
    }

    @Test
    void shouldKeepCandidateRowWhenAnyObjectFails() {
        when(fileRepository.findDeletedBefore(threshold)).thenReturn(List.of());
        when(candidateRepository.findDeletedBefore(threshold)).thenReturn(List.of(
                Candidate.builder().id(9L).photoKey("k-photo").resumeKey("k-resume").build()));
        doThrow(new ObsException("delete down")).when(obsStorage).delete("k-resume");

        int purged = service.purgeExpired(threshold);

        assertEquals(0, purged);
        verify(candidateRepository, never()).hardDeleteById(anyLong());
    }

    @Test
    void shouldHandleNullKeysGracefully() {
        when(fileRepository.findDeletedBefore(threshold)).thenReturn(List.of());
        when(candidateRepository.findDeletedBefore(threshold)).thenReturn(List.of(
                Candidate.builder().id(9L).build()));

        int purged = service.purgeExpired(threshold);

        // 无照片无简历：直接硬删行
        assertEquals(1, purged);
        verify(obsStorage, never()).delete(anyString());
        verify(candidateRepository).hardDeleteById(9L);
    }

    @Test
    void shouldNoopWhenNothingExpired() {
        when(fileRepository.findDeletedBefore(threshold)).thenReturn(List.of());
        when(candidateRepository.findDeletedBefore(threshold)).thenReturn(List.of());

        int purged = service.purgeExpired(threshold);

        assertEquals(0, purged);
        verifyNoInteractions(obsStorage);
        verify(fileRepository, never()).hardDeleteByStorageKey(anyString());
        verify(candidateRepository, never()).hardDeleteById(anyLong());
    }
}
