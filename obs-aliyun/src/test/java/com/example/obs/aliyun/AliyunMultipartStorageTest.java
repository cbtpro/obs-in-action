package com.example.obs.aliyun;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadResult;
import com.aliyun.oss.model.ListPartsRequest;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.PartListing;
import com.aliyun.oss.model.PartSummary;
import com.aliyun.oss.model.UploadPartRequest;
import com.aliyun.oss.model.UploadPartResult;
import com.example.obs.core.ObsException;
import com.example.obs.core.ObsPartInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AliyunMultipartStorage 单元测试。
 * 覆盖分片工作流透传（initiate/uploadPart/listParts/complete/abort）、
 * listParts 分页聚合、异常包装与 NoSuchUpload 幂等语义。
 */
class AliyunMultipartStorageTest {

    private AliyunProperties props;
    private OSS ossClient;
    private AliyunMultipartStorage storage;

    @BeforeEach
    void setUp() {
        props = new AliyunProperties();
        props.setBucketName("my-bucket");
        props.setEndpoint("oss-cn-hangzhou.aliyuncs.com");

        ossClient = mock(OSS.class);
        storage = new AliyunMultipartStorage(props, ossClient);
    }

    @Test
    void initiateShouldReturnUploadId() {
        InitiateMultipartUploadResult result = new InitiateMultipartUploadResult();
        result.setUploadId("upload-1");
        when(ossClient.initiateMultipartUpload(any(InitiateMultipartUploadRequest.class))).thenReturn(result);

        assertEquals("upload-1", storage.initiate("video.mp4", "video/mp4"));

        ArgumentCaptor<InitiateMultipartUploadRequest> captor =
                ArgumentCaptor.forClass(InitiateMultipartUploadRequest.class);
        verify(ossClient).initiateMultipartUpload(captor.capture());
        assertEquals("my-bucket", captor.getValue().getBucketName());
        assertEquals("video.mp4", captor.getValue().getKey());
        assertEquals("video/mp4", captor.getValue().getObjectMetadata().getContentType());
    }

    @Test
    void initiateShouldWrapException() {
        when(ossClient.initiateMultipartUpload(any(InitiateMultipartUploadRequest.class)))
                .thenThrow(new RuntimeException("network down"));

        assertThrows(ObsException.class, () -> storage.initiate("video.mp4", "video/mp4"));
    }

    @Test
    void uploadPartShouldReturnEtag() {
        UploadPartResult result = new UploadPartResult();
        result.setPartNumber(2);
        result.setETag("etag-2");
        when(ossClient.uploadPart(any(UploadPartRequest.class))).thenReturn(result);

        String etag = storage.uploadPart("video.mp4", "upload-1", 2, new byte[1024], "md5-base64");

        assertEquals("etag-2", etag);
        ArgumentCaptor<UploadPartRequest> captor = ArgumentCaptor.forClass(UploadPartRequest.class);
        verify(ossClient).uploadPart(captor.capture());
        assertEquals("my-bucket", captor.getValue().getBucketName());
        assertEquals("video.mp4", captor.getValue().getKey());
        assertEquals("upload-1", captor.getValue().getUploadId());
        assertEquals(2, captor.getValue().getPartNumber());
        assertEquals(1024L, captor.getValue().getPartSize());
        assertEquals("md5-base64", captor.getValue().getMd5Digest());
    }

    @Test
    void uploadPartShouldWrapException() {
        when(ossClient.uploadPart(any(UploadPartRequest.class))).thenThrow(new RuntimeException("network down"));

        assertThrows(ObsException.class,
                () -> storage.uploadPart("video.mp4", "upload-1", 1, new byte[10], "md5-base64"));
    }

    @Test
    void listPartsShouldAggregateAllPages() {
        PartListing page1 = listing(summary(1, "etag-1", 100), true, 1);
        PartListing page2 = listing(summary(2, "etag-2", 200), false, null);
        when(ossClient.listParts(any(ListPartsRequest.class))).thenReturn(page1, page2);

        List<ObsPartInfo> parts = storage.listParts("video.mp4", "upload-1");

        assertEquals(2, parts.size());
        assertEquals(new ObsPartInfo(1, "etag-1", 100), parts.get(0));
        assertEquals(new ObsPartInfo(2, "etag-2", 200), parts.get(1));

        ArgumentCaptor<ListPartsRequest> captor = ArgumentCaptor.forClass(ListPartsRequest.class);
        verify(ossClient, org.mockito.Mockito.times(2)).listParts(captor.capture());
        // 第二页应带上第一页返回的 nextPartNumberMarker
        assertEquals(1, captor.getAllValues().get(1).getPartNumberMarker());
    }

    @Test
    void listPartsShouldWrapException() {
        when(ossClient.listParts(any(ListPartsRequest.class))).thenThrow(new RuntimeException("network down"));

        assertThrows(ObsException.class, () -> storage.listParts("video.mp4", "upload-1"));
    }

    @Test
    void completeShouldPassPartETags() {
        storage.complete("video.mp4", "upload-1",
                List.of(new ObsPartInfo(1, "etag-1", 100), new ObsPartInfo(2, "etag-2", 200)), "video/mp4");

        ArgumentCaptor<CompleteMultipartUploadRequest> captor =
                ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
        verify(ossClient).completeMultipartUpload(captor.capture());
        List<PartETag> partETags = captor.getValue().getPartETags();
        assertEquals(2, partETags.size());
        assertEquals(1, partETags.get(0).getPartNumber());
        assertEquals("etag-1", partETags.get(0).getETag());
        assertEquals(2, partETags.get(1).getPartNumber());
    }

    @Test
    void completeShouldWrapException() {
        doThrowCompleteFailure();

        assertThrows(ObsException.class, () -> storage.complete(
                "video.mp4", "upload-1", List.of(new ObsPartInfo(1, "etag-1", 100)), "video/mp4"));
    }

    @Test
    void abortShouldTreatNoSuchUploadAsIdempotentSuccess() {
        // 构造参数顺序：message, errorCode, requestId, hostId, accessKeyId, signatureProvided, stringToSign
        OSSException noSuchUpload = new OSSException(
                "no such upload", "NoSuchUpload", "rid", "hid", "ak", "sig", "sts");
        org.mockito.Mockito.doThrow(noSuchUpload)
                .when(ossClient).abortMultipartUpload(any(AbortMultipartUploadRequest.class));

        // NoSuchUpload 视为已终止，静默成功不抛异常
        storage.abort("video.mp4", "upload-1");
    }

    @Test
    void abortShouldWrapNoSuchUploadLikeErrorButOtherCodes() {
        OSSException other = new OSSException(
                "access denied", "AccessDenied", "rid", "hid", "ak", "sig", "sts");
        org.mockito.Mockito.doThrow(other)
                .when(ossClient).abortMultipartUpload(any(AbortMultipartUploadRequest.class));

        assertThrows(ObsException.class, () -> storage.abort("video.mp4", "upload-1"));
    }

    @Test
    void abortShouldPassBucketKeyAndUploadId() {
        storage.abort("video.mp4", "upload-1");

        ArgumentCaptor<AbortMultipartUploadRequest> captor =
                ArgumentCaptor.forClass(AbortMultipartUploadRequest.class);
        verify(ossClient).abortMultipartUpload(captor.capture());
        assertEquals("my-bucket", captor.getValue().getBucketName());
        assertEquals("video.mp4", captor.getValue().getKey());
        assertEquals("upload-1", captor.getValue().getUploadId());
    }

    @Test
    void chunkSizeShouldBe5Mb() {
        assertEquals(5 * 1024 * 1024, storage.chunkSize());
    }

    @Test
    void initiateShouldSkipBlankMimeType() {
        InitiateMultipartUploadResult result = new InitiateMultipartUploadResult();
        result.setUploadId("upload-1");
        when(ossClient.initiateMultipartUpload(any(InitiateMultipartUploadRequest.class))).thenReturn(result);

        storage.initiate("video.mp4", null);

        ArgumentCaptor<InitiateMultipartUploadRequest> captor =
                ArgumentCaptor.forClass(InitiateMultipartUploadRequest.class);
        verify(ossClient).initiateMultipartUpload(captor.capture());
        // mimeType 为空时不设置 Content-Type，交给 OSS 默认行为
        assertTrue(captor.getValue().getObjectMetadata().getContentType() == null
                || captor.getValue().getObjectMetadata().getContentType().isBlank());
    }

    private void doThrowCompleteFailure() {
        org.mockito.Mockito.doThrow(new RuntimeException("network down"))
                .when(ossClient).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    private PartListing listing(PartSummary summary, boolean truncated, Integer nextMarker) {
        PartListing listing = new PartListing();
        listing.setParts(List.of(summary));
        listing.setTruncated(truncated);
        if (nextMarker != null) {
            listing.setNextPartNumberMarker(nextMarker);
        }
        return listing;
    }

    private PartSummary summary(int partNumber, String etag, long size) {
        PartSummary s = new PartSummary();
        s.setPartNumber(partNumber);
        s.setETag(etag);
        s.setSize(size);
        return s;
    }
}
