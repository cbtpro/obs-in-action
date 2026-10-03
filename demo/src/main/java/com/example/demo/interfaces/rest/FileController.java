package com.example.demo.interfaces.rest;

import com.example.demo.application.FileApplicationService;
import com.example.demo.application.FileDownloadService;
import com.example.demo.domain.model.FileEntity;
import com.example.demo.interfaces.rest.dto.FileItem;
import com.example.demo.interfaces.rest.dto.FileUploadResponse;
import com.example.demo.interfaces.rest.dto.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneId;
import java.util.List;

/**
 * 文件 REST 接口。
 * 仅做 HTTP 层职责：参数接收、DTO 转换、响应头处理；业务编排委托给应用服务。
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileApplicationService fileApplicationService;
    private final FileDownloadService fileDownloadService;

    /**
     * 上传文件。
     */
    @PostMapping("/upload")
    public Result<FileUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        FileApplicationService.UploadResult result = fileApplicationService.upload(file);
        return Result.success(FileUploadResponse.builder()
                .key(result.getKey())
                .name(result.getName())
                .url(result.getUrl())
                .size(result.getSize())
                .contentType(result.getContentType())
                .image(result.isImage())
                .build());
    }

    /**
     * 回显/下载文件。响应语义（类型/长度/Disposition/扩展头）由 FileDownloadService 决定，
     * 加密下载等增强在实现层接入，此处不变。
     */
    @GetMapping("/{key}")
    public ResponseEntity<Resource> download(@PathVariable String key) {
        FileDownloadService.FileDownload download = fileDownloadService.download(key);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(parseMediaType(download.contentType()));
        // 禁止浏览器内容嗅探，杜绝 MIME 伪装文件的存储型 XSS
        headers.add("X-Content-Type-Options", "nosniff");
        if (download.contentLength() >= 0) {
            headers.setContentLength(download.contentLength());
        }
        headers.add(HttpHeaders.CONTENT_DISPOSITION, download.contentDisposition());
        // 扩展响应头（如后续下载加密的密钥元信息）
        download.extraHeaders().forEach(headers::add);
        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(download.stream()));
    }

    /**
     * 列出已上传文件。
     */
    @GetMapping
    public Result<List<FileItem>> list(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        List<FileEntity> entities = fileApplicationService.list(limit);
        List<FileItem> items = entities.stream().map(e -> FileItem.builder()
                .key(e.getStorageKey())
                .name(e.getName())
                .size(e.getSize())
                .mimeType(e.getContentType())
                .putTime(e.getCreatedAt() != null
                        ? e.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : 0)
                .build()).toList();
        return Result.success(items);
    }

    /**
     * 删除文件。
     */
    @DeleteMapping("/{key}")
    public Result<Void> delete(@PathVariable String key) {
        fileApplicationService.delete(key);
        return Result.success();
    }

    private MediaType parseMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
