package com.example.demo.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分片上传 init 请求体。
 * fingerprint 由客户端按文件名+大小+修改时间计算，用于断点续传时找回会话；
 * contentHash 是完整文件 SHA-256，用于内容秒传。
 */
@Data
public class MultipartInitRequest {

    @NotBlank
    @Size(max = 500)
    private String name;

    @NotNull
    @Positive
    private Long size;

    @NotBlank
    @Size(max = 200)
    private String contentType;

    @NotBlank
    @Size(max = 128)
    private String fingerprint;

    /** 文件完整内容的 SHA-256 十六进制摘要，用于秒传查重 */
    @NotBlank
    @Size(min = 64, max = 64)
    private String contentHash;
}
