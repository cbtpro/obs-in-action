package com.example.demo.interfaces.rest;

import com.example.demo.application.FileApplicationService;
import com.example.demo.application.FileDownloadService;
import com.example.demo.interfaces.rest.exception.BizErrorCode;
import com.example.demo.interfaces.rest.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * FileController 下载端点的 HTTP 映射测试。
 * 关注点：载荷 → 响应头的组装（nosniff/长度/Disposition/扩展头）、
 * 未知长度跳过 Content-Length、非法 Content-Type 的兜底、业务异常透传给全局处理器。
 */
@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileApplicationService fileApplicationService;

    @Mock
    private FileDownloadService fileDownloadService;

    @InjectMocks
    private FileController controller;

    private FileDownloadService.FileDownload payload(String contentType, long contentLength,
                                                      String disposition, Map<String, String> extraHeaders) {
        return new FileDownloadService.FileDownload(
                new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)),
                contentType, contentLength, disposition, extraHeaders);
    }

    @Test
    void downloadShouldMapPayloadToHttpResponse() {
        when(fileDownloadService.download("k1")).thenReturn(payload(
                "image/png", 4L, "inline; filename*=UTF-8''a.png",
                Map.of("X-Crypto-IV", "dnNyPQ==")));

        ResponseEntity<org.springframework.core.io.Resource> resp = controller.download("k1");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(MediaType.IMAGE_PNG, resp.getHeaders().getContentType());
        assertEquals("nosniff", resp.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("inline; filename*=UTF-8''a.png",
                resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals(4L, resp.getHeaders().getContentLength());
        // 扩展头（加密元信息等）原样透传
        assertEquals("dnNyPQ==", resp.getHeaders().getFirst("X-Crypto-IV"));
        // 响应体是流本身
        InputStreamResource body = (InputStreamResource) resp.getBody();
        assertNotNull(body);
        assertDoesNotThrow(() -> {
            String content = new String(body.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("data", content);
        });
    }

    @Test
    void downloadShouldSkipContentLengthWhenUnknown() {
        when(fileDownloadService.download("k2")).thenReturn(payload(
                "image/png", -1L, "inline; filename*=UTF-8''a.png", null));

        ResponseEntity<org.springframework.core.io.Resource> resp = controller.download("k2");

        // 未知长度：不设 Content-Length（走 chunked），而不是下发错误的 0
        assertFalse(resp.getHeaders().containsKey(HttpHeaders.CONTENT_LENGTH));
        assertEquals(-1L, resp.getHeaders().getContentLength());
    }

    @Test
    void downloadShouldFallbackToOctetStreamOnBlankContentType() {
        when(fileDownloadService.download("k3")).thenReturn(payload(
                null, 4L, "attachment; filename*=UTF-8''a.bin", null));

        ResponseEntity<org.springframework.core.io.Resource> resp = controller.download("k3");

        assertEquals(MediaType.APPLICATION_OCTET_STREAM, resp.getHeaders().getContentType());
    }

    @Test
    void downloadShouldFallbackToOctetStreamOnInvalidContentType() {
        when(fileDownloadService.download("k4")).thenReturn(payload(
                "not a mime/###", 4L, "attachment; filename*=UTF-8''a.bin", null));

        ResponseEntity<org.springframework.core.io.Resource> resp = controller.download("k4");

        // 非法 Content-Type 兜底 octet-stream，而不是 500
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, resp.getHeaders().getContentType());
    }

    @Test
    void downloadShouldPropagateBizExceptionToAdvice() {
        // key 不存在：BizException 原样上抛，由 GlobalExceptionHandler 统一映射 404
        when(fileDownloadService.download("gone")).thenThrow(new BizException(BizErrorCode.NOT_FOUND));

        assertThrows(BizException.class, () -> controller.download("gone"));
    }
}
