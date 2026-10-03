package com.example.demo.application;

import com.example.demo.domain.model.FileEntity;
import com.example.demo.domain.repository.FileRepository;
import com.example.demo.interfaces.rest.exception.BizErrorCode;
import com.example.demo.interfaces.rest.exception.BizException;
import com.example.obs.core.ObsDownloadResult;
import com.example.obs.core.ObsStorage;
import com.example.obs.core.util.FileTypeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 直传下载实现：对象存储的流原样下发，不做任何加工。
 * inline/attachment 策略与文件名编码在此决定——加密实现若要整体接管（如强制 attachment），
 * 装饰本类并按需覆写各字段即可。
 */
@Service
@RequiredArgsConstructor
public class DirectFileDownloadService implements FileDownloadService {

    private final FileRepository fileRepository;
    private final ObsStorage obsStorage;

    @Override
    public FileDownload download(String key) {
        FileEntity entity = fileRepository.findByStorageKey(key)
                .orElseThrow(() -> new BizException(BizErrorCode.NOT_FOUND));
        ObsDownloadResult raw = obsStorage.download(key);
        return new FileDownload(
                raw.stream(),
                raw.contentType(),
                raw.contentLength(),
                buildDisposition(raw.contentType(), entity.getName()),
                Map.of());
    }

    /**
     * 仅白名单图片（jpeg/png/gif/webp/bmp）与 PDF 允许 inline 预览，其余强制 attachment。
     * 不用 startsWith("image/")，防止 image/svg+xml 等可执行内容被 inline 渲染。
     */
    private String buildDisposition(String contentType, String originalName) {
        String encoded = URLEncoder.encode(originalName, StandardCharsets.UTF_8).replace("+", "%20");
        boolean inline = FileTypeResolver.isImage(contentType, originalName)
                || (contentType != null && contentType.toLowerCase().contains("pdf"));
        return (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encoded;
    }
}
