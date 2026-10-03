package com.example.obs.core;

/**
 * 对象存储统一抽象接口。
 * 七牛云、阿里云 OSS 等各厂商实现此接口，业务层通过此接口编程，不感知底层存储。
 * 新增厂商只需实现此接口 + 注册 AutoConfiguration，符合开闭原则。
 */
public interface ObsStorage {

    /**
     * 上传文件到对象存储。
     *
     * @param request 上传请求（数据 + key + 元信息）
     * @return 上传结果（key + 可访问 url + 大小 + 类型）
     */
    ObsUploadResult upload(ObsUploadRequest request);

    /**
     * 下载文件，返回二进制流。
     *
     * @param key 存储 key
     * @return 下载结果（流 + Content-Type + 大小）
     */
    ObsDownloadResult download(String key);

    /**
     * 删除对象。对象不存在视为成功（幂等）。
     *
     * @param key 存储 key
     */
    void delete(String key);

    /**
     * 获取对象的访问 URL。
     * 公有 bucket 返回永久直链；私有 bucket 返回带签名的临时访问 URL。
     *
     * @param key 存储 key
     * @return 可直接访问的 URL
     */
    String getUrl(String key);
}
