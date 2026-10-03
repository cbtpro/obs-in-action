package com.example.obs.core;

import java.util.List;

/**
 * 对象存储分片上传（Multipart Upload）抽象接口。
 * 与 {@link ObsStorage} 并列：单文件上传场景不感知本接口，需要分片能力的实现同时实现两个接口。
 *
 * 生命周期：initiate 拿到 uploadId → uploadPart 逐片上传 → complete 拼接为完整对象；
 * 中途放弃调用 abort。listParts 查询云端已确认的分片，是断点续传的权威数据源。
 */
public interface ObsMultipartStorage {

    /**
     * 发起分片上传会话。
     *
     * @param key       对象存储 key（服务端生成）
     * @param mimeType  完整对象的 Content-Type（各厂商在不同阶段生效：阿里云在 init、七牛在 complete）
     * @return 云端会话标识 uploadId（阿里云/七牛 V2 均为云端生成，全局唯一）
     */
    String initiate(String key, String mimeType);

    /**
     * 上传单个分片。同一 partNumber 重复上传会覆盖云端已有分片（重试安全）。
     *
     * @param key        对象存储 key
     * @param uploadId   会话标识
     * @param partNumber 分片序号，从 1 开始
     * @param data       分片数据
     * @param contentMd5 分片内容的 Base64 MD5，用于云端传输完整性校验
     * @return 云端返回的分片标识 etag，complete 时回传
     */
    String uploadPart(String key, String uploadId, int partNumber, byte[] data, String contentMd5);

    /**
     * 列出云端已上传成功的分片（按 partNumber 升序）。
     * 断点续传的唯一权威：本地记录了但云端未确认的分片不算数。
     *
     * @param key      对象存储 key
     * @param uploadId 会话标识
     * @return 已确认分片列表；会话不存在时厂商行为不同（阿里云抛异常、七牛返回 612），实现层统一抛 ObsException
     */
    List<ObsPartInfo> listParts(String key, String uploadId);

    /**
     * 通知云端将已上传分片按 partNumber 升序拼接为完整对象。
     * 成功后 uploadId 失效，后续相关操作均失败。
     *
     * @param key      对象存储 key
     * @param uploadId 会话标识
     * @param parts    分片清单（partNumber + etag），应来自 listParts 的云端权威数据
     * @param mimeType 完整对象的 Content-Type
     */
    void complete(String key, String uploadId, List<ObsPartInfo> parts, String mimeType);

    /**
     * 终止会话并删除云端已上传的分片。会话已失效时静默成功（幂等）。
     *
     * @param key      对象存储 key
     * @param uploadId 会话标识
     */
    void abort(String key, String uploadId);

    /**
     * 单片大小（字节）。
     * 分片约束由厂商决定（七牛非末片最小 1MB、阿里云最小 100KB），由实现层给出统一取值，
     * 业务层不感知差异。
     */
    int chunkSize();
}
