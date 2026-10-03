package com.example.demo.interfaces.rest;

import com.example.demo.application.MultipartUploadApplicationService;
import com.example.obs.core.ObsPartInfo;
import com.example.demo.interfaces.rest.dto.FileUploadResponse;
import com.example.demo.interfaces.rest.dto.MultipartInitRequest;
import com.example.demo.interfaces.rest.dto.MultipartInitResponse;
import com.example.demo.interfaces.rest.dto.MultipartPartItem;
import com.example.demo.interfaces.rest.dto.MultipartPartResponse;
import com.example.demo.interfaces.rest.dto.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分片上传 REST 接口。
 * 仅做 HTTP 层职责：参数接收、DTO 转换；会话生命周期编排委托给应用服务。
 * 分片数据以 application/octet-stream 原始字节直传，不经过 multipart/form-data 包装。
 */
@Slf4j
@RestController
@RequestMapping("/api/multipart")
@RequiredArgsConstructor
public class MultipartController {

    private final MultipartUploadApplicationService multipartService;

    /**
     * 发起分片上传（或按指纹续传）。
     */
    @PostMapping("/init")
    public Result<MultipartInitResponse> init(@Valid @RequestBody MultipartInitRequest request) {
        MultipartUploadApplicationService.InitResult result = multipartService.init(
                new MultipartUploadApplicationService.InitCommand(
                        request.getName(), request.getSize(), request.getContentType(),
                        request.getFingerprint(), request.getContentHash()));
        return Result.success(MultipartInitResponse.builder()
                .sessionId(result.getSessionId())
                .chunkSize(result.getChunkSize())
                .resumed(result.isResumed())
                .instantUpload(result.isInstantUpload())
                .file(result.getFile() == null ? null : toFileResponse(result.getFile()))
                .uploadedParts(toItems(result.getUploadedParts()))
                .build());
    }

    /**
     * 上传单个分片，返回云端 etag。
     */
    @PutMapping(value = "/{sessionId}/parts/{partNumber}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Result<MultipartPartResponse> uploadPart(@PathVariable long sessionId,
                                                    @PathVariable int partNumber,
                                                    @RequestHeader("Content-MD5") String contentMd5,
                                                    @RequestBody byte[] data) {
        String etag = multipartService.uploadPart(sessionId, partNumber, data, contentMd5);
        return Result.success(MultipartPartResponse.builder().etag(etag).build());
    }

    /**
     * 完成分片上传：云端拼接对象并落文件元数据，返回与单文件上传同构的响应。
     */
    @PostMapping("/{sessionId}/complete")
    public Result<FileUploadResponse> complete(@PathVariable long sessionId) {
        MultipartUploadApplicationService.CompleteResult result = multipartService.complete(sessionId);
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
     * 终止分片上传：云端删除已传分片，会话作废（幂等）。
     */
    @DeleteMapping("/{sessionId}")
    public Result<Void> abort(@PathVariable long sessionId) {
        multipartService.abort(sessionId);
        return Result.success();
    }

    private List<MultipartPartItem> toItems(List<ObsPartInfo> parts) {
        return parts.stream()
                .map(p -> MultipartPartItem.builder()
                        .partNumber(p.partNumber())
                        .etag(p.etag())
                        .size(p.size())
                        .build())
                .toList();
    }

    private FileUploadResponse toFileResponse(MultipartUploadApplicationService.CompleteResult result) {
        return FileUploadResponse.builder()
                .key(result.getKey())
                .name(result.getName())
                .url(result.getUrl())
                .size(result.getSize())
                .contentType(result.getContentType())
                .image(result.isImage())
                .build();
    }
}
