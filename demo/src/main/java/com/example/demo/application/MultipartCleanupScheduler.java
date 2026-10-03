package com.example.demo.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 孤儿分片会话清理定时任务。
 * 由 file.multipart.cleanup.enabled 控制（默认关闭）：置为 true 并配置 cron/retention-days 即启用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "file.multipart.cleanup", name = "enabled", havingValue = "true")
public class MultipartCleanupScheduler {

    private final MultipartSessionCleanupService cleanupService;
    private final MultipartCleanupProperties properties;

    @Scheduled(cron = "${file.multipart.cleanup.cron:0 0 4 * * ?}")
    public void purgeExpired() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(properties.getRetentionDays());
        int purged = cleanupService.purgeExpired(threshold);
        if (purged > 0) {
            log.info("定时孤儿会话清理完成: retentionDays={} purged={}", properties.getRetentionDays(), purged);
        }
    }
}
