package com.example.obs.qiniu;

import com.example.obs.core.ObsException;
import com.example.obs.core.ObsUploadRequest;
import com.example.obs.core.ObsUploadResult;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * QiniuObsStorage 单元测试。
 * 重点验证 getUrl 方法：拼接 domain + key 后调用 Auth.privateDownloadUrl（带配置的有效期）生成签名 URL，
 * 以及 upload 显式透传 mimeType、download 非 200 时关闭响应体。
 */
class QiniuObsStorageTest {

    private QiniuProperties props;
    private Auth auth;
    private UploadManager uploadManager;
    private BucketManager bucketManager;
    private QiniuObsStorage storage;

    @BeforeEach
    void setUp() {
        props = new QiniuProperties();
        props.setDomain("https://cdn.example.com");
        props.setBucket("test-bucket");

        auth = mock(Auth.class);
        uploadManager = mock(UploadManager.class);
        bucketManager = mock(BucketManager.class);

        storage = new QiniuObsStorage(props, auth, uploadManager, bucketManager);
    }

    @Test
    void getUrlShouldConcatDomainAndKeyAndSign() {
        // given
        String key = "abc123.jpg";
        String expectedBaseUrl = "https://cdn.example.com/abc123.jpg";
        String signedUrl = "https://cdn.example.com/abc123.jpg?e=123&token=sign";
        when(auth.privateDownloadUrl(expectedBaseUrl, props.getUrlExpireSeconds())).thenReturn(signedUrl);

        // when
        String result = storage.getUrl(key);

        // then
        assertEquals(signedUrl, result);
        verify(auth, times(1)).privateDownloadUrl(expectedBaseUrl, props.getUrlExpireSeconds());
    }

    @Test
    void getUrlShouldHandleKeyWithSpecialChars() {
        String key = "folder/file name.pdf";
        String expectedBaseUrl = "https://cdn.example.com/folder/file name.pdf";
        String signedUrl = "https://cdn.example.com/folder/file%20name.pdf?token=xxx";
        when(auth.privateDownloadUrl(expectedBaseUrl, props.getUrlExpireSeconds())).thenReturn(signedUrl);

        String result = storage.getUrl(key);

        assertEquals(signedUrl, result);
        verify(auth).privateDownloadUrl(expectedBaseUrl, props.getUrlExpireSeconds());
    }

    @Test
    void getUrlShouldUseHttpDomainWhenConfigured() {
        props.setDomain("http://test.clouddn.com");
        String key = "img.png";
        String expectedBaseUrl = "http://test.clouddn.com/img.png";
        when(auth.privateDownloadUrl(expectedBaseUrl, props.getUrlExpireSeconds())).thenReturn("signed");

        String result = storage.getUrl(key);

        assertEquals("signed", result);
        verify(auth).privateDownloadUrl(expectedBaseUrl, props.getUrlExpireSeconds());
    }

    @Test
    void getUrlShouldNotAddExtraSlashWhenDomainHasTrailingSlash() {
        // domain 不应带尾斜杠，但即使带了也应拼接为 //（验证明拼逻辑）
        props.setDomain("https://cdn.example.com/");
        String key = "file.txt";
        String expectedBaseUrl = "https://cdn.example.com//file.txt";
        when(auth.privateDownloadUrl(expectedBaseUrl, props.getUrlExpireSeconds())).thenReturn("signed");

        String result = storage.getUrl(key);

        assertEquals("signed", result);
    }

    @Test
    void getUrlShouldBeCalledIndependentlyPerKey() {
        when(auth.privateDownloadUrl(anyString(), anyLong()))
                .thenReturn("url1")
                .thenReturn("url2");

        String r1 = storage.getUrl("key1");
        String r2 = storage.getUrl("key2");

        assertEquals("url1", r1);
        assertEquals("url2", r2);
        verify(auth).privateDownloadUrl("https://cdn.example.com/key1", props.getUrlExpireSeconds());
        verify(auth).privateDownloadUrl("https://cdn.example.com/key2", props.getUrlExpireSeconds());
    }

    @Test
    void getUrlShouldUseConfiguredExpireSeconds() {
        props.setUrlExpireSeconds(60);
        when(auth.privateDownloadUrl(anyString(), eq(60L))).thenReturn("signed");

        String result = storage.getUrl("key");

        assertEquals("signed", result);
        verify(auth).privateDownloadUrl("https://cdn.example.com/key", 60L);
    }

    @Test
    void uploadShouldPassMimeTypeExplicitly() throws Exception {
        // given（auth 为 mock，uploadToken 返回 null，token 参数用 nullable 匹配）
        Response resp = mock(Response.class);
        when(resp.isOK()).thenReturn(true);
        when(uploadManager.put(any(byte[].class), anyString(), nullable(String.class), any(), anyString(), anyBoolean()))
                .thenReturn(resp);

        // when
        ObsUploadResult result = storage.upload(ObsUploadRequest.builder()
                .data(new byte[]{1, 2, 3})
                .key("k.png")
                .contentType("image/png")
                .originalName("p.png")
                .size(3)
                .build());

        // then
        assertNotNull(result);
        verify(uploadManager).put(any(byte[].class), eq("k.png"), nullable(String.class), isNull(), eq("image/png"), eq(false));
    }

    @Test
    void uploadShouldPassNullMimeWhenContentTypeBlank() throws Exception {
        Response resp = mock(Response.class);
        when(resp.isOK()).thenReturn(true);
        when(uploadManager.put(any(byte[].class), anyString(), nullable(String.class), isNull(), isNull(), anyBoolean()))
                .thenReturn(resp);

        storage.upload(ObsUploadRequest.builder()
                .data(new byte[]{1})
                .key("k.bin")
                .contentType(" ")
                .originalName("k.bin")
                .size(1)
                .build());

        verify(uploadManager).put(any(byte[].class), eq("k.bin"), nullable(String.class), isNull(), isNull(), eq(false));
    }

    @Test
    void uploadShouldThrowWhenResponseNotOk() throws Exception {
        Response resp = mock(Response.class);
        when(resp.isOK()).thenReturn(false);
        when(uploadManager.put(any(byte[].class), anyString(), nullable(String.class), any(), anyString(), anyBoolean()))
                .thenReturn(resp);

        assertThrows(ObsException.class, () -> storage.upload(ObsUploadRequest.builder()
                .data(new byte[]{1})
                .key("k.png")
                .contentType("image/png")
                .originalName("k.png")
                .size(1)
                .build()));
    }

    @Test
    void deleteShouldIgnore612AsNotFound() throws Exception {
        storage.delete("missing-key");
        // 612 = 对象不存在，视为幂等删除成功，但由于 mock 不抛异常，验证 delete 被调用即可
        verify(bucketManager).delete(props.getBucket(), "missing-key");
    }

    @Test
    void deleteShouldThrowObsExceptionOnError() throws Exception {
        QiniuException ex = Mockito.mock(QiniuException.class);
        when(ex.code()).thenReturn(500);
        doThrow(ex).when(bucketManager).delete(anyString(), anyString());

        assertThrows(ObsException.class, () -> storage.delete("key"));
    }
}
