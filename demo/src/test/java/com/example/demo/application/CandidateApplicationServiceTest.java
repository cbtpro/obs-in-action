package com.example.demo.application;

import com.example.demo.domain.FileKeyGenerator;
import com.example.demo.domain.model.Candidate;
import com.example.demo.domain.repository.CandidateRepository;
import com.example.demo.interfaces.rest.exception.BizErrorCode;
import com.example.demo.interfaces.rest.exception.BizException;
import com.example.obs.core.ObsException;
import com.example.obs.core.ObsStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CandidateApplicationService 单元测试。
 * 覆盖 getAccessUrl、上传策略校验、事务补偿（create/save 失败回滚新对象）、
 * update 替换文件的顺序（先传新 → 保存 → 后删旧）与 delete 的 DB 优先删除。
 */
class CandidateApplicationServiceTest {

    /** 校验码合法的测试身份证号（1949-12-31，末位 X） */
    private static final String VALID_ID_CARD = "11010519491231002X";

    private CandidateRepository candidateRepository;
    private ObsStorage obsStorage;
    private FileKeyGenerator fileKeyGenerator;
    private CandidateApplicationService service;

    @BeforeEach
    void setUp() {
        candidateRepository = mock(CandidateRepository.class);
        obsStorage = mock(ObsStorage.class);
        fileKeyGenerator = mock(FileKeyGenerator.class);
        // 真实策略（默认值）+ 真实清理器（委托 mock 的 obsStorage），便于 verify delete 调用
        service = new CandidateApplicationService(candidateRepository, obsStorage, fileKeyGenerator,
                new FileUploadPolicy(), new ObsStorageCleaner(obsStorage));
    }

    @Test
    void getAccessUrlShouldReturnNullWhenKeyIsNull() {
        assertNull(service.getAccessUrl(null));
        verifyNoInteractions(obsStorage);
    }

    @Test
    void getAccessUrlShouldReturnNullWhenKeyIsBlank() {
        assertNull(service.getAccessUrl(""));
        assertNull(service.getAccessUrl("   "));
        verifyNoInteractions(obsStorage);
    }

    @Test
    void getAccessUrlShouldDelegateToObsStorageWhenKeyPresent() {
        String key = "abc123.jpg";
        String url = "https://cdn.example.com/abc123.jpg?token=xxx";
        when(obsStorage.getUrl(key)).thenReturn(url);

        String result = service.getAccessUrl(key);

        assertEquals(url, result);
        verify(obsStorage).getUrl(key);
    }

    @Test
    void getAccessUrlShouldDelegateDifferentKeysIndependently() {
        when(obsStorage.getUrl("key1")).thenReturn("url1");
        when(obsStorage.getUrl("key2")).thenReturn("url2");

        assertEquals("url1", service.getAccessUrl("key1"));
        assertEquals("url2", service.getAccessUrl("key2"));
        assertNull(service.getAccessUrl(null));
    }

    @Test
    void createShouldRejectNonImagePhoto() {
        when(candidateRepository.findByIdCard(any())).thenReturn(Optional.empty());
        MockMultipartFile photo = new MockMultipartFile("photo", "p.txt", "text/plain", new byte[]{1});

        BizException ex = assertThrows(BizException.class,
                () -> service.create("张三", VALID_ID_CARD, null, photo, null));

        assertEquals(BizErrorCode.TYPE_NOT_ALLOWED, ex.getErrorCode());
        verify(obsStorage, never()).upload(any());
    }

    @Test
    void createShouldRollbackUploadedObjectsWhenSaveFails() {
        when(candidateRepository.findByIdCard(any())).thenReturn(Optional.empty());
        when(fileKeyGenerator.generate(any())).thenReturn("k-photo", "k-resume");
        when(candidateRepository.save(any())).thenThrow(new RuntimeException("db down"));
        MockMultipartFile photo = new MockMultipartFile("photo", "p.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile resume = new MockMultipartFile("resume", "r.pdf", "application/pdf", new byte[]{1});

        assertThrows(BizException.class,
                () -> service.create("张三", VALID_ID_CARD, null, photo, resume));

        // DB 失败后补偿删除本次上传的两个对象
        verify(obsStorage).delete("k-photo");
        verify(obsStorage).delete("k-resume");
    }

    @Test
    void updateShouldKeepOldFileWhenNewUploadFails() {
        Candidate existing = Candidate.builder()
                .id(1L).name("张三").idCard(VALID_ID_CARD).gender("男").age(70)
                .photoKey("old-photo").photoName("old.jpg")
                .build();
        when(candidateRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(fileKeyGenerator.generate(any())).thenReturn("new-photo");
        when(obsStorage.upload(any())).thenThrow(new ObsException("upload down"));
        MockMultipartFile photo = new MockMultipartFile("photo", "new.jpg", "image/jpeg", new byte[]{1});

        // 存储层异常按既有语义透传 ObsException（全局处理器兜底 500）
        assertThrows(ObsException.class, () -> service.update(1L, "张三", null, null, photo, null));

        // 修复点：上传失败时旧文件不得被删除（旧实现先删旧文件会造成数据丢失）
        verify(obsStorage, never()).delete(anyString());
    }

    @Test
    void updateShouldUploadNewThenSaveThenDeleteOld() {
        Candidate existing = Candidate.builder()
                .id(1L).name("张三").idCard(VALID_ID_CARD).gender("男").age(70)
                .photoKey("old-photo").photoName("old.jpg")
                .build();
        when(candidateRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(fileKeyGenerator.generate(any())).thenReturn("new-photo");
        when(candidateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MockMultipartFile photo = new MockMultipartFile("photo", "new.jpg", "image/jpeg", new byte[]{1});

        Candidate updated = service.update(1L, "张三", null, null, photo, null);

        assertEquals("new-photo", updated.getPhotoKey());
        // 顺序：先上传新文件 → 保存 → 再删除旧文件（单测无事务同步，afterCommit 回退为立即删除）
        InOrder inOrder = inOrder(obsStorage, candidateRepository);
        inOrder.verify(obsStorage).upload(any());
        inOrder.verify(candidateRepository).save(any());
        inOrder.verify(obsStorage).delete("old-photo");
    }

    @Test
    void deleteShouldSoftDeleteWithoutTouchingStorage() {
        Candidate existing = Candidate.builder()
                .id(1L).name("张三").idCard(VALID_ID_CARD).gender("男").age(70)
                .photoKey("k-photo").resumeKey("k-resume")
                .build();
        when(candidateRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        // L2 软删除：只打标，不动对象存储（物理清理由定时任务在保留期后执行）
        verify(candidateRepository).softDeleteById(eq(1L), any(LocalDateTime.class));
        verify(obsStorage, never()).delete(anyString());
        verify(candidateRepository, never()).hardDeleteById(anyLong());
    }

    @Test
    void deleteShouldRejectAlreadySoftDeleted() {
        // findById 过滤软删数据：已删除的候选人视为不存在
        when(candidateRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(BizException.class, () -> service.delete(1L));

        verify(candidateRepository, never()).softDeleteById(anyLong(), any());
    }
}
