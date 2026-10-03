package com.example.demo.application;

import java.io.InputStream;
import java.util.Map;

/**
 * 文件下载服务接口：封装"取元数据 + 取对象流 + 决定响应语义"的完整下载流程。
 * 当前为直传实现（{@link DirectFileDownloadService}）；
 * 后续下载加密等功能新增实现类（或装饰现有实现）即可，Controller 与调用方不变。
 */
public interface FileDownloadService {

    /**
     * 按存储 key 下载文件。
     *
     * @param key 对象存储 key
     * @return 下载载荷（流 + 完整响应语义）
     * @throws com.example.demo.interfaces.rest.exception.BizException key 不存在（40404）
     */
    FileDownload download(String key);

    /**
     * 下载载荷：一次下载需要下发的全部信息。
     * <p>
     * 载荷直接持有"响应语义"（类型/长度/Disposition/扩展头），
     * 加密实现才能整体接管——密文流的长度、类型、预览策略都会变化。
     *
     * @param stream             文件内容流（调用方负责消费/关闭）
     * @param contentType        浏览器渲染依据
     * @param contentLength      内容长度；未知为 -1（不设 Content-Length，走 chunked）
     * @param contentDisposition 完整 Disposition 值（inline/attachment + RFC 5987 文件名）
     * @param extraHeaders       扩展响应头（如加密元信息），默认空
     */
    record FileDownload(InputStream stream,
                        String contentType,
                        long contentLength,
                        String contentDisposition,
                        Map<String, String> extraHeaders) {

        public FileDownload {
            extraHeaders = extraHeaders == null ? Map.of() : Map.copyOf(extraHeaders);
        }
    }
}
