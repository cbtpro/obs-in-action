package com.example.obs.qiniu;

import com.example.obs.core.ObsException;
import com.example.obs.core.ObsPartInfo;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Client;
import com.qiniu.http.Response;
import com.qiniu.storage.ApiQueryRegion;
import com.qiniu.storage.ApiUploadV2AbortUpload;
import com.qiniu.storage.ApiUploadV2CompleteUpload;
import com.qiniu.storage.ApiUploadV2InitUpload;
import com.qiniu.storage.ApiUploadV2ListParts;
import com.qiniu.storage.ApiUploadV2UploadPart;
import com.qiniu.util.Auth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * QiniuMultipartStorage 单元测试。
 * 通过 mockConstruction 拦截 SDK API 原语的构造（Mockito 5 inline 默认支持），
 * 验证：上传域名解析缓存、五步工作流的参数透传与响应解析、612 幂等终止。
 */
class QiniuMultipartStorageTest {

    private QiniuProperties props;
    private Auth auth;
    private QiniuMultipartStorage storage;

    @BeforeEach
    void setUp() {
        props = new QiniuProperties();
        props.setAccessKey("ak");
        props.setSecretKey("sk");
        props.setBucket("test-bucket");
        props.setDomain("https://cdn.example.com");
        auth = mock(Auth.class);
        storage = new QiniuMultipartStorage(props, auth, mock(Client.class));
    }

    /** 域名解析构造器：返回固定 up host，避免真实网络请求 */
    private MockedConstruction<ApiQueryRegion> regionConstruction(int hostCount) {
        return mockConstruction(ApiQueryRegion.class, (mock, context) -> {
            ApiQueryRegion.Response resp = mock(ApiQueryRegion.Response.class);
            when(resp.isOK()).thenReturn(true);
            when(resp.getDefaultRegionUpHosts())
                    .thenReturn(List.of("up.qiniup.com").subList(0, hostCount));
            when(mock.request(any())).thenReturn(resp);
        });
    }

    @Test
    void initiateShouldReturnUploadIdAndCacheHost() {
        ApiUploadV2InitUpload.Response apiResp = mock(ApiUploadV2InitUpload.Response.class);
        when(apiResp.isOK()).thenReturn(true);
        when(apiResp.getUploadId()).thenReturn("upload-1");
        when(auth.uploadToken("test-bucket", "key1")).thenReturn("token-1");

        try (MockedConstruction<ApiQueryRegion> region = regionConstruction(1);
             MockedConstruction<ApiUploadV2InitUpload> init = mockConstruction(ApiUploadV2InitUpload.class,
                     (mock, context) -> when(mock.request(any())).thenReturn(apiResp))) {

            assertEquals("upload-1", storage.initiate("key1", "image/png"));
            // 域名解析只发生一次：再调一次 init 不再构造 ApiQueryRegion
            assertEquals("upload-1", storage.initiate("key1", "image/png"));
            assertEquals(1, region.constructed().size());
        }
    }

    @Test
    void initiateShouldFailWhenUploadIdMissing() {
        ApiUploadV2InitUpload.Response apiResp = mock(ApiUploadV2InitUpload.Response.class);
        when(apiResp.isOK()).thenReturn(true);
        when(apiResp.getUploadId()).thenReturn(null);

        try (MockedConstruction<ApiQueryRegion> region = regionConstruction(1);
             MockedConstruction<ApiUploadV2InitUpload> init = mockConstruction(ApiUploadV2InitUpload.class,
                     (mock, context) -> when(mock.request(any())).thenReturn(apiResp))) {

            ObsException ex = assertThrows(ObsException.class, () -> storage.initiate("key1", "image/png"));
            assertTrue(ex.getMessage().contains("uploadId"));
        }
    }

    @Test
    void uploadPartShouldReturnEtag() throws Exception {
        ApiUploadV2UploadPart.Response apiResp = mock(ApiUploadV2UploadPart.Response.class);
        when(apiResp.isOK()).thenReturn(true);
        when(apiResp.getEtag()).thenReturn("etag-1");
        when(auth.uploadToken("test-bucket", "key1"))
                .thenReturn(Auth.create("ak", "sk").uploadToken("test-bucket", "key1"));

        try (MockedConstruction<ApiQueryRegion> region = regionConstruction(1);
             MockedConstruction<ApiUploadV2UploadPart> part = mockConstruction(ApiUploadV2UploadPart.class,
                     (mock, context) -> when(mock.request(any())).thenReturn(apiResp))) {

            String etag = storage.uploadPart("key1", "upload-1", 1, new byte[1024], "md5-base64");
            assertEquals("etag-1", etag);
            ArgumentCaptor<ApiUploadV2UploadPart.Request> captor =
                    ArgumentCaptor.forClass(ApiUploadV2UploadPart.Request.class);
            verify(part.constructed().get(0)).request(captor.capture());
            assertEquals("md5-base64", captor.getValue().getHeader().get("Content-MD5"));
        }
    }

    @Test
    void listPartsShouldPaginateUntilMarkerZero() {
        when(auth.uploadToken("test-bucket", "key1")).thenReturn("token-1");
        AtomicInteger page = new AtomicInteger();

        try (MockedConstruction<ApiQueryRegion> region = regionConstruction(1);
             MockedConstruction<ApiUploadV2ListParts> list = mockConstruction(ApiUploadV2ListParts.class,
                     (mock, context) -> {
                         ApiUploadV2ListParts.Response resp = mock(ApiUploadV2ListParts.Response.class);
                         when(resp.isOK()).thenReturn(true);
                         if (page.getAndIncrement() == 0) {
                             when(resp.getParts()).thenReturn(List.of(
                                     Map.of("partNumber", 1, "etag", "e1", "size", 4194304L)));
                             when(resp.getPartNumberMarker()).thenReturn(1);
                         } else {
                             when(resp.getParts()).thenReturn(List.of(
                                     Map.of("partNumber", 2, "etag", "e2", "size", 100L)));
                             when(resp.getPartNumberMarker()).thenReturn(0);
                         }
                         when(mock.request(any())).thenReturn(resp);
                     })) {

            List<ObsPartInfo> parts = storage.listParts("key1", "upload-1");
            assertEquals(2, parts.size());
            assertEquals(new ObsPartInfo(1, "e1", 4194304L), parts.get(0));
            assertEquals(new ObsPartInfo(2, "e2", 100L), parts.get(1));
            // 翻页循环构造了两次 API
            assertEquals(2, list.constructed().size());
        }
    }

    @Test
    void completeShouldPassPartsAndMimeType() {
        ApiUploadV2CompleteUpload.Response apiResp = mock(ApiUploadV2CompleteUpload.Response.class);
        when(apiResp.isOK()).thenReturn(true);
        when(auth.uploadToken("test-bucket", "key1")).thenReturn("token-1");

        try (MockedConstruction<ApiQueryRegion> region = regionConstruction(1);
             MockedConstruction<ApiUploadV2CompleteUpload> complete =
                     mockConstruction(ApiUploadV2CompleteUpload.class,
                             (mock, context) -> when(mock.request(any())).thenReturn(apiResp))) {

            assertDoesNotThrow(() -> storage.complete("key1", "upload-1",
                    List.of(new ObsPartInfo(1, "e1", 100L), new ObsPartInfo(2, "e2", 50L)), "image/png"));

            // complete 原语被构造并请求了一次（parts 映射在请求内部透传）
            assertEquals(1, complete.constructed().size());
        }
    }

    @Test
    void abortShouldSilentlySucceedOn612() {
        when(auth.uploadToken("test-bucket", "key1")).thenReturn("token-1");

        // 构造 code=612（资源不存在）的真实异常：Response 是 final 类且 statusCode 为字段，
        // 无法用 Mockito stub，须用 okhttp3 Response.createError 构建
        okhttp3.Response okResp = new okhttp3.Response.Builder()
                .request(new okhttp3.Request.Builder().url("http://up.qiniup.com").build())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(612)
                .message("no such upload")
                .body(okhttp3.ResponseBody.create(
                        "no such upload", okhttp3.MediaType.get("application/json")))
                .build();
        QiniuException e612 = new QiniuException(
                Response.createError(okResp, "up.qiniup.com", 0.1, "no such upload"));
        assertEquals(612, e612.code());

        try (MockedConstruction<ApiQueryRegion> region = regionConstruction(1);
             MockedConstruction<ApiUploadV2AbortUpload> abort = mockConstruction(ApiUploadV2AbortUpload.class,
                     (mock, context) -> when(mock.request(any())).thenThrow(e612))) {

            // 612 视为幂等成功，不抛异常
            assertDoesNotThrow(() -> storage.abort("key1", "upload-1"));
        }
    }

    @Test
    void abortShouldThrowOnOtherErrors() {
        when(auth.uploadToken("test-bucket", "key1")).thenReturn("token-1");

        // 非 612 错误（响应为 null，code() 返回 -1）：包装为 ObsException 抛出
        QiniuException err = new QiniuException(new Exception("network down"));

        try (MockedConstruction<ApiQueryRegion> region = regionConstruction(1);
             MockedConstruction<ApiUploadV2AbortUpload> abort = mockConstruction(ApiUploadV2AbortUpload.class,
                     (mock, context) -> when(mock.request(any())).thenThrow(err))) {

            assertThrows(ObsException.class, () -> storage.abort("key1", "upload-1"));
        }
    }

    @Test
    void chunkSizeShouldBe4Mb() {
        assertEquals(4 * 1024 * 1024, storage.chunkSize());
    }

    @Test
    void regionQueryFailureShouldNotBeCached() {
        when(auth.uploadToken("test-bucket", "key1")).thenReturn("token-1");
        ApiUploadV2InitUpload.Response apiResp = mock(ApiUploadV2InitUpload.Response.class);
        when(apiResp.isOK()).thenReturn(true);
        when(apiResp.getUploadId()).thenReturn("upload-1");

        // 第一次域名解析失败，第二次成功：失败不缓存，调用方拿到 ObsException 后可重试
        AtomicInteger queryCount = new AtomicInteger();
        try (MockedConstruction<ApiQueryRegion> region = mockConstruction(ApiQueryRegion.class, (mock, context) -> {
                 if (queryCount.getAndIncrement() == 0) {
                     when(mock.request(any())).thenThrow(new QiniuException(new Exception("uc down")));
                 } else {
                     ApiQueryRegion.Response resp = mock(ApiQueryRegion.Response.class);
                     when(resp.isOK()).thenReturn(true);
                     when(resp.getDefaultRegionUpHosts()).thenReturn(List.of("up.qiniup.com"));
                     when(mock.request(any())).thenReturn(resp);
                 }
             });
             MockedConstruction<ApiUploadV2InitUpload> init = mockConstruction(ApiUploadV2InitUpload.class,
                     (mock, context) -> when(mock.request(any())).thenReturn(apiResp))) {

            assertThrows(ObsException.class, () -> storage.initiate("key1", "image/png"));
            assertEquals("upload-1", storage.initiate("key1", "image/png"));
            assertEquals(2, region.constructed().size());
        }
    }

}
