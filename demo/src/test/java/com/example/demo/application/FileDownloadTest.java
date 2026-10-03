package com.example.demo.application;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileDownload 载荷的契约测试。
 * 这份契约是给未来的加密实现定的：extraHeaders 必须非 null 且不可变，
 * 防止下游控制器遍历时被意外篡改或并发修改。
 */
class FileDownloadTest {

    private FileDownloadService.FileDownload payload(Map<String, String> headers) {
        return new FileDownloadService.FileDownload(
                new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)),
                "image/png", 4L, "inline; filename*=UTF-8''a.png", headers);
    }

    @Test
    void shouldNormalizeNullExtraHeadersToEmpty() {
        // 加密实现不设扩展头时传 null 是合法的，载荷方不得抛 NPE
        FileDownloadService.FileDownload d = payload(null);

        assertNotNull(d.extraHeaders());
        assertTrue(d.extraHeaders().isEmpty());
    }

    @Test
    void shouldDefensivelyCopyExtraHeaders() {
        Map<String, String> src = new HashMap<>();
        src.put("X-Crypto-IV", "dnNyPQ==");

        FileDownloadService.FileDownload d = payload(src);

        // 构造后再改源 Map，不影响载荷
        src.put("X-Crypto-IV", "tampered");
        assertEquals("dnNyPQ==", d.extraHeaders().get("X-Crypto-IV"));

        // 载荷上的头集合不可修改，杜绝下游篡改
        assertThrows(UnsupportedOperationException.class,
                () -> d.extraHeaders().put("X-Evil", "1"));
    }
}
