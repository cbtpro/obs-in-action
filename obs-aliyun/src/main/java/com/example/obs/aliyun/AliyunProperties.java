package com.example.obs.aliyun;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置属性。
 */
@Data
@ConfigurationProperties(prefix = "aliyun.oss")
public class AliyunProperties {

    /** Access Key Id */
    private String accessKeyId;
    /** Access Key Secret */
    private String accessKeySecret;
    /** 地域节点（不含 bucket），如 oss-cn-hangzhou.aliyuncs.com */
    private String endpoint;
    /** Bucket 名称 */
    private String bucketName;
}
