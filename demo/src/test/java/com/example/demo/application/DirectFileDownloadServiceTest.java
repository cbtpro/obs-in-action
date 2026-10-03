package com.example.demo.application;

import com.example.demo.domain.model.FileEntity;
import com.example.demo.domain.repository.FileRepository;
import com.example.demo.interfaces.rest.exception.BizErrorCode;
import com.example.demo.interfaces.rest.exception.BizException;
import com.example.obs.core.ObsDownloadResult;
import com.example.obs.core.ObsStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DirectFileDownloadService 单元测试。
 * 覆盖：载荷透传、inline/attachment 策略、中文文件名 RFC 5987 编码、不存在 key 的 404。
 */
class DirectFileDownloadServiceTest {

    private FileRepository fileRepository;
    private ObsStorage obsStorage;
    private DirectFileDownloadService service;

    @BeforeEach
    void setUp() {
        fileRepository = mock(FileRepository.class);
        obsStorage = mock(ObsStorage.class);
        service = new DirectFileDownloadService(fileRepository, obsStorage);
    }

    private void stub(String key, String name, String contentType, long length) {
        when(fileRepository.findByStorageKey(key)).thenReturn(Optional.of(
                FileEntity.builder().storageKey(key).name(name).contentType(contentType).build()));
        when(obsStorage.download(key)).thenReturn(new ObsDownloadResult(
                new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)), contentType, length));
    }

    @Test
    void shouldPassthroughStreamAndMetadata() {
        stub("k1", "a.png", "image/png", 4L);

        FileDownloadService.FileDownload d = service.download("k1");

        assertEquals("image/png", d.contentType());
        assertEquals(4L, d.contentLength());
        assertNotNull(d.stream());
        assertTrue(d.extraHeaders().isEmpty());
    }

    @Test
    void shouldInlineWhitelistedImageAndPdf() {
        stub("k1", "a.png", "image/png", 4L);
        assertTrue(service.download("k1").contentDisposition().startsWith("inline"));

        stub("k2", "b.pdf", "application/pdf", 4L);
        assertTrue(service.download("k2").contentDisposition().startsWith("inline"));
    }

    @Test
    void shouldForceAttachmentForOtherTypes() {
        stub("k3", "c.zip", "application/zip", 4L);

        String disposition = service.download("k3").contentDisposition();

        assertTrue(disposition.startsWith("attachment"));
    }

    @Test
    void shouldNeverInlineSvgEvenIfContentTypeForged() {
        // 即使存储侧 Content-Type 被伪装为 svg，也不允许 inline（防脚本执行）
        stub("k4", "evil.svg", "image/svg+xml", 4L);

        String disposition = service.download("k4").contentDisposition();

        assertTrue(disposition.startsWith("attachment"));
    }

    @Test
    void shouldEncodeChineseFilenameWithRfc5987() {
        stub("k5", "简历-张三.pdf", "application/pdf", 4L);

        String disposition = service.download("k5").contentDisposition();

        assertTrue(disposition.contains("filename*=UTF-8''"));
        assertTrue(disposition.contains("%E7%AE%80%E5%8E%86"));   // "简历" 的 UTF-8 编码
        assertFalse(disposition.contains("简历"));                    // 原文不直接出现在头里
    }

    @Test
    void shouldRejectUnknownLength() {
        // 云端未返回长度时为 -1，接口层据此跳过 Content-Length（走 chunked）
        stub("k6", "d.png", "image/png", -1L);

        assertEquals(-1L, service.download("k6").contentLength());
    }

    @Test
    void shouldThrowNotFoundForMissingKey() {
        when(fileRepository.findByStorageKey("gone")).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class, () -> service.download("gone"));

        assertEquals(BizErrorCode.NOT_FOUND.getCode(), e.getErrorCode().getCode());
        verify(obsStorage, never()).download(anyString());
    }
}
