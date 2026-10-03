package com.example.demo.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 孤儿分片会话清理任务配置。
 * enabled 默认关闭：清理只影响未完成会话，按需开启。
 */
@Data
@ConfigurationProperties(prefix = "file.multipart.cleanup")
public class MultipartCleanupProperties {

    /** 是否启用定时清理（默认关闭，测试通过后按需开启） */
    private boolean enabled = false;

    /** 会话保留天数：创建超过该天数仍未完成的会话视为孤儿 */
    private int retentionDays = 1;

    /** 清理任务 cron 表达式（默认每天凌晨 4 点） */
    private String cron = "0 0 4 * * ?";
}
