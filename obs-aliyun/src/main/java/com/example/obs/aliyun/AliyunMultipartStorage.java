package com.example.obs.aliyun;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.ListPartsRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.PartListing;
import com.aliyun.oss.model.PartSummary;
import com.aliyun.oss.model.UploadPartRequest;
import com.aliyun.oss.model.UploadPartResult;
import com.example.obs.core.ObsException;
import com.example.obs.core.ObsMultipartStorage;
import com.example.obs.core.ObsPartInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 阿里云 OSS 分片上传实现。
 * 原生 Multipart Upload API 直译：initiate/uploadPart/listParts/complete/abort 与接口一一对应。
 */
@Slf4j
@RequiredArgsConstructor
public class AliyunMultipartStorage implements ObsMultipartStorage {

    /** 单片 5MB：非末片最小 100KB，取 5MB 平衡请求数与单片传输时间 */
    private static final int CHUNK_SIZE = 5 * 1024 * 1024;

    /** listParts 单页上限（阿里云固定最大值 1000） */
    private static final int MAX_PARTS_PER_PAGE = 1000;

    private final AliyunProperties props;
    private final OSS ossClient;

    @Override
    public String initiate(String key, String mimeType) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            if (mimeType != null && !mimeType.isBlank()) {
                metadata.setContentType(mimeType);
            }
            InitiateMultipartUploadRequest request =
                    new InitiateMultipartUploadRequest(props.getBucketName(), key, metadata);
            return ossClient.initiateMultipartUpload(request).getUploadId();
        } catch (Exception e) {
            throw new ObsException("阿里云 OSS 发起分片上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadPart(String key, String uploadId, int partNumber, byte[] data, String contentMd5) {
        try {
            UploadPartRequest request = new UploadPartRequest();
            request.setBucketName(props.getBucketName());
            request.setKey(key);
            request.setUploadId(uploadId);
            request.setPartNumber(partNumber);
            request.setInputStream(new ByteArrayInputStream(data));
            request.setPartSize(data.length);
            request.setMd5Digest(contentMd5);
            UploadPartResult result = ossClient.uploadPart(request);
            return result.getPartETag().getETag();
        } catch (Exception e) {
            throw new ObsException("阿里云 OSS 分片上传失败: partNumber=" + partNumber + ", " + e.getMessage(), e);
        }
    }

    @Override
    public List<ObsPartInfo> listParts(String key, String uploadId) {
        try {
            List<ObsPartInfo> parts = new ArrayList<>();
            Integer marker = null;
            // 分页拉全量：单页最大 1000，文件大于 5GB（1000 片 × 5MB）时需要翻页
            while (true) {
                ListPartsRequest request = new ListPartsRequest(props.getBucketName(), key, uploadId);
                request.setMaxParts(MAX_PARTS_PER_PAGE);
                if (marker != null) {
                    request.setPartNumberMarker(marker);
                }
                PartListing listing = ossClient.listParts(request);
                for (PartSummary summary : listing.getParts()) {
                    parts.add(new ObsPartInfo(summary.getPartNumber(), summary.getETag(), summary.getSize()));
                }
                if (!listing.isTruncated() || listing.getNextPartNumberMarker() == null) {
                    return parts;
                }
                marker = listing.getNextPartNumberMarker();
            }
        } catch (Exception e) {
            throw new ObsException("阿里云 OSS 列举分片失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void complete(String key, String uploadId, List<ObsPartInfo> parts, String mimeType) {
        try {
            List<PartETag> partETags = parts.stream()
                    .map(p -> new PartETag(p.partNumber(), p.etag()))
                    .toList();
            CompleteMultipartUploadRequest request =
                    new CompleteMultipartUploadRequest(props.getBucketName(), key, uploadId, partETags);
            ossClient.completeMultipartUpload(request);
        } catch (Exception e) {
            throw new ObsException("阿里云 OSS 完成分片上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void abort(String key, String uploadId) {
        try {
            ossClient.abortMultipartUpload(new AbortMultipartUploadRequest(props.getBucketName(), key, uploadId));
        } catch (com.aliyun.oss.OSSException e) {
            // NoSuchUpload：会话不存在（已完成或已终止），视为幂等成功
            if ("NoSuchUpload".equals(e.getErrorCode())) {
                log.warn("阿里云 OSS 分片会话不存在，视为已终止: key={} uploadId={}", key, uploadId);
                return;
            }
            throw new ObsException("阿里云 OSS 终止分片上传失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ObsException("阿里云 OSS 终止分片上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public int chunkSize() {
        return CHUNK_SIZE;
    }
}
