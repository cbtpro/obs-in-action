package com.example.obs.aliyun;

import com.aliyun.oss.OSS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AliyunObsStorage 单元测试。
 * 重点验证 getUrl 方法：拼接 bucket.endpoint + key 生成公有直链。
 */
class AliyunObsStorageTest {

    private AliyunProperties props;
    private OSS ossClient;
    private AliyunObsStorage storage;

    @BeforeEach
    void setUp() {
        props = new AliyunProperties();
        props.setBucketName("my-bucket");
        props.setEndpoint("oss-cn-hangzhou.aliyuncs.com");

        ossClient = mock(OSS.class);
        storage = new AliyunObsStorage(props, ossClient);
    }

    @Test
    void getUrlShouldReturnPermanentDirectLink() {
        String key = "photo.jpg";
        String result = storage.getUrl(key);

        assertEquals("https://my-bucket.oss-cn-hangzhou.aliyuncs.com/photo.jpg", result);
        // 公有 bucket getUrl 不需要调用 OSS 客户端
        verifyNoInteractions(ossClient);
    }

    @Test
    void getUrlShouldHandleKeyWithSubDirectory() {
        String key = "folder/sub/file.pdf";
        String result = storage.getUrl(key);

        assertEquals("https://my-bucket.oss-cn-hangzhou.aliyuncs.com/folder/sub/file.pdf", result);
    }

    @Test
    void getUrlShouldWorkWithDifferentEndpoint() {
        props.setEndpoint("oss-cn-shanghai.aliyuncs.com");
        String result = storage.getUrl("test.txt");

        assertEquals("https://my-bucket.oss-cn-shanghai.aliyuncs.com/test.txt", result);
    }

    @Test
    void deleteShouldCallOssDeleteObject() {
        storage.delete("key");
        verify(ossClient).deleteObject("my-bucket", "key");
    }

    @Test
    void deleteShouldThrowObsExceptionOnError() {
        doThrow(new RuntimeException("network error"))
                .when(ossClient).deleteObject(anyString(), anyString());

        assertThrows(com.example.obs.core.ObsException.class, () -> storage.delete("key"));
    }
}
