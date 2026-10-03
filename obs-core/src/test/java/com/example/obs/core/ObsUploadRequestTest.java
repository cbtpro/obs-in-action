package com.example.obs.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ObsUploadRequest 单元测试。
 * 验证 record 的构造与字段访问。
 */
class ObsUploadRequestTest {

    @Test
    void shouldBuildAndGetFields() {
        byte[] data = "hello".getBytes();
        ObsUploadRequest req = ObsUploadRequest.builder()
                .data(data)
                .key("test-key.jpg")
                .contentType("image/jpeg")
                .originalName("photo.jpg")
                .size(5)
                .build();

        assertArrayEquals(data, req.data());
        assertEquals("test-key.jpg", req.key());
        assertEquals("image/jpeg", req.contentType());
        assertEquals("photo.jpg", req.originalName());
        assertEquals(5, req.size());
    }
}
