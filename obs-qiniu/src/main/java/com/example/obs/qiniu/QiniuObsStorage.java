package com.example.obs.qiniu;

import com.example.obs.core.ObsDownloadResult;
import com.example.obs.core.ObsException;
import com.example.obs.core.ObsStorage;
import com.example.obs.core.ObsUploadRequest;
import com.example.obs.core.ObsUploadResult;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 七牛云 ObsStorage 实现。
 * 封装七牛 SDK 的上传/下载/删除，对外只暴露 ObsStorage 接口。
 */
@Slf4j
@RequiredArgsConstructor
public class QiniuObsStorage implements ObsStorage {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final QiniuProperties props;
    private final Auth auth;
    private final UploadManager uploadManager;
    private final BucketManager bucketManager;

    @Override
    public ObsUploadResult upload(ObsUploadRequest request) {
        String key = request.key();
        try {
            String upToken = auth.uploadToken(props.getBucket(), key);
            // 显式传递 mimeType，避免七牛按 key 扩展名探测（防止 evil.svg 被存为 image/svg+xml）
            // contentType 为空时传 null，七牛回退为默认 MIME 由服务端探测
            String mime = (request.contentType() == null || request.contentType().isBlank())
                    ? null : request.contentType();
            Response resp = uploadManager.put(request.data(), key, upToken, null, mime, false);
            if (!resp.isOK()) {
                log.error("七牛上传失败: status={} body={}", resp.statusCode, resp.bodyString());
                throw new ObsException("七牛上传失败: " + resp.statusCode);
            }
            String url = props.getDomain() + "/" + key;
            return ObsUploadResult.builder()
                    .key(key)
                    .url(url)
                    .size(request.size())
                    .contentType(request.contentType())
                    .build();
        } catch (QiniuException e) {
            throw new ObsException("七牛上传异常: " + e.getMessage(), e);
        }
    }

    @Override
    public ObsDownloadResult download(String key) {
        try {
            String privateUrl = auth.privateDownloadUrl(props.getDomain() + "/" + key);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(privateUrl))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                // 非 200 时主动关闭响应体，避免连接泄漏
                try {
                    resp.body().close();
                } catch (Exception closeEx) {
                    log.warn("关闭七牛下载响应体失败: key={}", key, closeEx);
                }
                throw new ObsException("七牛下载失败: " + resp.statusCode());
            }
            String contentType = resp.headers().firstValue("Content-Type").orElse("application/octet-stream");
            long len = resp.headers().firstValue("Content-Length")
                    .map(Long::parseLong).orElse(-1L);
            return new ObsDownloadResult(resp.body(), contentType, len);
        } catch (ObsException e) {
            throw e;
        } catch (Exception e) {
            throw new ObsException("七牛下载异常: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            bucketManager.delete(props.getBucket(), key);
        } catch (QiniuException e) {
            // 612 = 对象不存在，视为幂等删除成功
            if (e.code() != 612) {
                log.error("七牛删除失败: key={} code={} msg={}", key, e.code(), e.getMessage());
                throw new ObsException("七牛删除失败: " + e.getMessage(), e);
            }
            log.warn("七牛对象不存在，视为已删: key={}", key);
        }
    }

    @Override
    public String getUrl(String key) {
        String baseUrl = props.getDomain() + "/" + key;
        // 私有 bucket 用 privateDownloadUrl 生成带签名的临时访问 URL，有效期可配置
        return auth.privateDownloadUrl(baseUrl, props.getUrlExpireSeconds());
    }
}
