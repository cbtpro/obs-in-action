package com.example.demo.application;

import com.example.obs.core.ObsStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务感知的对象存储清理器。
 * 供各应用服务复用两类清理语义：
 * - deleteAfterCommit：事务提交后再删对象，DB 回滚时文件保全（删除失败仅告警，孤儿对象优于僵尸元数据）
 * - deleteQuietly：立即删除并吞掉异常（用于上传后 DB 失败的补偿回滚）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ObsStorageCleaner {

    private final ObsStorage obsStorage;

    /**
     * 事务提交后删除对象存储对象；无活动事务时直接删除。
     * 注意：afterCommit 在请求线程上同步执行，其中的异常必须自捕获（抛出会导致事务已提交但接口 500）。
     */
    public void deleteAfterCommit(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(key);
                }
            });
        } else {
            deleteQuietly(key);
        }
    }

    /**
     * 立即删除对象并吞掉异常（删除失败仅告警），key 为空则跳过。
     */
    public void deleteQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            obsStorage.delete(key);
        } catch (Exception e) {
            log.warn("删除对象存储失败（孤儿对象，可人工/对账清理）: key={}", key, e);
        }
    }
}
