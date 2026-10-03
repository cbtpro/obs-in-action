package com.example.demo.interfaces.rest.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 分片上传 uploadPart 响应体。
 */
@Data
@Builder
public class MultipartPartResponse {

    private String etag;
}
