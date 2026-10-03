package com.example.demo.application;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 物理清理任务配置。
 * enabled 默认关闭：软删除只打标不影响线上行为，物理清理按需开启。
 */
@Data
@ConfigurationProperties(prefix = "file.cleanup")
public class FileCleanupProperties {

    /** 是否启用定时物理清理（默认关闭，测试通过后按需开启） */
    private boolean enabled = false;

    /** 软删除保留天数：打标超过该天数后才物理清理 */
    private int retentionDays = 7;

    /** 清理任务 cron 表达式（默认每天凌晨 3 点） */
    private String cron = "0 0 3 * * ?";
}
