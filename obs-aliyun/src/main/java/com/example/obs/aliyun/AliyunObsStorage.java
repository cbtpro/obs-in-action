package com.example.obs.aliyun;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.example.obs.core.ObsDownloadResult;
import com.example.obs.core.ObsException;
import com.example.obs.core.ObsStorage;
import com.example.obs.core.ObsUploadRequest;
import com.example.obs.core.ObsUploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 阿里云 OSS ObsStorage 实现。
 * 封装阿里云 OSS SDK 的上传/下载/删除，对外只暴露 ObsStorage 接口。
 */
@Slf4j
@RequiredArgsConstructor
public class AliyunObsStorage implements ObsStorage {

    private final AliyunProperties props;
    private final OSS ossClient;

    @Override
    public ObsUploadResult upload(ObsUploadRequest request) {
        String key = request.key();
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            if (request.contentType() != null) {
                metadata.setContentType(request.contentType());
            }
            metadata.setContentLength(request.size());
            PutObjectRequest putRequest = new PutObjectRequest(
                    props.getBucketName(), key, new ByteArrayInputStream(request.data()), metadata);
            ossClient.putObject(putRequest);
            String url = "https://" + props.getBucketName() + "." + props.getEndpoint() + "/" + key;
            return ObsUploadResult.builder()
                    .key(key)
                    .url(url)
                    .size(request.size())
                    .contentType(request.contentType())
                    .build();
        } catch (Exception e) {
            throw new ObsException("阿里云 OSS 上传异常: " + e.getMessage(), e);
        }
    }

    @Override
    public ObsDownloadResult download(String key) {
        try {
            OSSObject ossObject = ossClient.getObject(props.getBucketName(), key);
            InputStream stream = ossObject.getObjectContent();
            ObjectMetadata meta = ossObject.getObjectMetadata();
            String contentType = meta.getContentType();
            long len = meta.getContentLength();
            return new ObsDownloadResult(stream, contentType, len);
        } catch (Exception e) {
            throw new ObsException("阿里云 OSS 下载异常: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            ossClient.deleteObject(props.getBucketName(), key);
        } catch (Exception e) {
            log.error("阿里云 OSS 删除失败: key={} msg={}", key, e.getMessage());
            throw new ObsException("阿里云 OSS 删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getUrl(String key) {
        // 公有 bucket 直接返回永久直链；如需私有访问可改用 generatePresignedUrl
        return "https://" + props.getBucketName() + "." + props.getEndpoint() + "/" + key;
    }
}
