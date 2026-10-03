package com.example.demo.interfaces.rest.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 云端已确认分片信息（客户端据此跳过已传分片）。
 */
@Data
@Builder
public class MultipartPartItem {

    private int partNumber;
    private String etag;
    private long size;
}
