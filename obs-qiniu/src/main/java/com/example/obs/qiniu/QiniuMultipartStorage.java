package com.example.obs.qiniu;

import com.example.obs.core.ObsException;
import com.example.obs.core.ObsMultipartStorage;
import com.example.obs.core.ObsPartInfo;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Client;
import com.qiniu.storage.ApiQueryRegion;
import com.qiniu.storage.ApiUploadV2AbortUpload;
import com.qiniu.storage.ApiUploadV2CompleteUpload;
import com.qiniu.storage.ApiUploadV2InitUpload;
import com.qiniu.storage.ApiUploadV2ListParts;
import com.qiniu.storage.ApiUploadV2UploadPart;
import com.qiniu.util.Auth;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 七牛云分片上传实现（V2 Multipart API）。
 * 复用 SDK 公开的 ApiUploadV2* 原语，与断点续传 SDK（ResumeUploader）走同一套云端协议，
 * 但由服务端持有会话，分片请求逐个透传，云端已确认分片通过 listParts 查询。
 *
 * 与阿里云实现的两点差异：
 * 1. 上传域名需先查询 UC（ApiQueryRegion 为公开接口，无需签名），查询结果进程内缓存；
 * 2. mimeType 在 complete 阶段生效（阿里云在 init 阶段），uploadId 云端固定 7 天过期。
 */
@Slf4j
public class QiniuMultipartStorage implements ObsMultipartStorage {

    /** 单片 4MB：七牛非末片最小 1MB，取 4MB 平衡请求数与单片传输时间 */
    private static final int CHUNK_SIZE = 4 * 1024 * 1024;

    /** listParts 单页上限（七牛固定最大值 1000） */
    private static final int MAX_PARTS_PER_PAGE = 1000;

    /** 指定资源不存在（uploadId 不存在或已 complete），abort 幂等处理 */
    private static final int CODE_NO_SUCH_UPLOAD = 612;

    private final QiniuProperties props;
    private final Auth auth;
    private final Client client;

    /** 上传域名缓存（查询 UC 一次，进程内复用；bucket 迁移域名需重启生效） */
    private volatile String upUrlPrefix;

    public QiniuMultipartStorage(QiniuProperties props, Auth auth, Client client) {
        this.props = props;
        this.auth = auth;
        this.client = client;
    }

    @Override
    public String initiate(String key, String mimeType) {
        try {
            ApiUploadV2InitUpload api = new ApiUploadV2InitUpload(client);
            ApiUploadV2InitUpload.Request request =
                    new ApiUploadV2InitUpload.Request(upUrlPrefix(), token(key)).setKey(key);
            ApiUploadV2InitUpload.Response response = api.request(request);
            if (!response.isOK()) {
                throw asException("七牛发起分片上传失败", response.getResponse().statusCode);
            }
            String uploadId = response.getUploadId();
            if (uploadId == null || uploadId.isBlank()) {
                throw new ObsException("七牛发起分片上传失败: uploadId 为空");
            }
            return uploadId;
        } catch (QiniuException e) {
            throw new ObsException("七牛发起分片上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadPart(String key, String uploadId, int partNumber, byte[] data, String contentMd5) {
        try {
            ApiUploadV2UploadPart api = new ApiUploadV2UploadPart(client);
            ApiUploadV2UploadPart.Request request = new ApiUploadV2UploadPart.Request(
                    upUrlPrefix(), token(key), uploadId, partNumber)
                    .setKey(key)
                    .setUploadData(data, 0, data.length, null);
            request.addHeaderField("Content-MD5", contentMd5);
            ApiUploadV2UploadPart.Response response = api.request(request);
            if (!response.isOK()) {
                throw asException("七牛分片上传失败: partNumber=" + partNumber, response.getResponse().statusCode);
            }
            String etag = response.getEtag();
            if (etag == null || etag.isBlank()) {
                throw new ObsException("七牛分片上传失败: etag 为空, partNumber=" + partNumber);
            }
            return etag;
        } catch (QiniuException e) {
            throw new ObsException("七牛分片上传失败: partNumber=" + partNumber + ", " + e.getMessage(), e);
        }
    }

    @Override
    public List<ObsPartInfo> listParts(String key, String uploadId) {
        try {
            List<ObsPartInfo> parts = new ArrayList<>();
            Integer marker = null;
            // 分页拉全量：partNumberMarker 为 0 表示列举结束
            while (true) {
                ApiUploadV2ListParts api = new ApiUploadV2ListParts(client);
                ApiUploadV2ListParts.Request request = new ApiUploadV2ListParts.Request(
                        upUrlPrefix(), token(key), uploadId)
                        .setKey(key)
                        .setMaxParts(MAX_PARTS_PER_PAGE);
                if (marker != null && marker > 0) {
                    request.setPartNumberMarker(marker);
                }
                ApiUploadV2ListParts.Response response = api.request(request);
                if (!response.isOK()) {
                    throw asException("七牛列举分片失败", response.getResponse().statusCode);
                }
                for (Object item : response.getParts()) {
                    parts.add(toPartInfo(item));
                }
                marker = response.getPartNumberMarker();
                if (marker == null || marker == 0) {
                    return parts;
                }
            }
        } catch (QiniuException e) {
            throw new ObsException("七牛列举分片失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void complete(String key, String uploadId, List<ObsPartInfo> parts, String mimeType) {
        try {
            List<Map<String, Object>> partsInfo = new ArrayList<>(parts.size());
            for (ObsPartInfo part : parts) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put(ApiUploadV2CompleteUpload.Request.PART_NUMBER, part.partNumber());
                info.put(ApiUploadV2CompleteUpload.Request.PART_ETG, part.etag());
                partsInfo.add(info);
            }
            ApiUploadV2CompleteUpload api = new ApiUploadV2CompleteUpload(client);
            ApiUploadV2CompleteUpload.Request request = new ApiUploadV2CompleteUpload.Request(
                    upUrlPrefix(), token(key), uploadId, partsInfo)
                    .setKey(key)
                    .setFileName(key);
            if (mimeType != null && !mimeType.isBlank()) {
                request.setFileMimeType(mimeType);
            }
            ApiUploadV2CompleteUpload.Response response = api.request(request);
            if (!response.isOK()) {
                throw asException("七牛完成分片上传失败", response.getResponse().statusCode);
            }
        } catch (QiniuException e) {
            throw new ObsException("七牛完成分片上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void abort(String key, String uploadId) {
        try {
            ApiUploadV2AbortUpload api = new ApiUploadV2AbortUpload(client);
            ApiUploadV2AbortUpload.Request request = new ApiUploadV2AbortUpload.Request(
                    upUrlPrefix(), token(key), uploadId).setKey(key);
            ApiUploadV2AbortUpload.Response response = api.request(request);
            if (!response.isOK()) {
                throw asException("七牛终止分片上传失败", response.getResponse().statusCode);
            }
        } catch (QiniuException e) {
            // 612 = uploadId 不存在（已完成或已终止），视为幂等成功
            if (e.code() != CODE_NO_SUCH_UPLOAD) {
                throw new ObsException("七牛终止分片上传失败: " + e.getMessage(), e);
            }
            log.warn("七牛分片会话不存在，视为已终止: key={} uploadId={}", key, uploadId);
        }
    }

    @Override
    public int chunkSize() {
        return CHUNK_SIZE;
    }

    /**
     * 解析上传域名：查询 UC（公开接口，无需签名），取默认 region 的第一个上传域名。
     * 查询失败不缓存，下次调用重试。
     */
    private String upUrlPrefix() throws QiniuException {
        String prefix = this.upUrlPrefix;
        if (prefix != null) {
            return prefix;
        }
        ApiQueryRegion query = new ApiQueryRegion(client);
        ApiQueryRegion.Response response = query.request(
                new ApiQueryRegion.Request(null, props.getAccessKey(), props.getBucket()));
        if (!response.isOK()) {
            throw new ObsException("七牛查询上传域名失败: " + response.getResponse().statusCode);
        }
        List<String> hosts = response.getDefaultRegionUpHosts();
        if (hosts == null || hosts.isEmpty()) {
            throw new ObsException("七牛查询上传域名失败: 未返回上传域名");
        }
        this.upUrlPrefix = "https://" + hosts.get(0);
        return this.upUrlPrefix;
    }

    /** 生成 bucket:key 作用域的上传凭证（本地计算无网络开销，每次调用现签） */
    private String token(String key) {
        return auth.uploadToken(props.getBucket(), key);
    }

    @SuppressWarnings("unchecked")
    private ObsPartInfo toPartInfo(Object item) {
        if (!(item instanceof Map)) {
            throw new ObsException("七牛列举分片失败: 分片数据格式异常");
        }
        Map<String, Object> map = (Map<String, Object>) item;
        Object partNumber = map.get("partNumber");
        Object etag = map.get("etag");
        Object size = map.get("size");
        if (!(partNumber instanceof Number number) || !(etag instanceof String etagStr)) {
            throw new ObsException("七牛列举分片失败: 分片数据缺失 partNumber/etag");
        }
        return new ObsPartInfo(number.intValue(), etagStr,
                size instanceof Number sizeNum ? sizeNum.longValue() : 0L);
    }

    private ObsException asException(String action, int statusCode) {
        return new ObsException(action + ": " + statusCode);
    }
}
