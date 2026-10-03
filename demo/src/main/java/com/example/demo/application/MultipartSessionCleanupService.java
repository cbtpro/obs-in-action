package com.example.demo.application;

import com.example.demo.domain.model.UploadSession;
import com.example.demo.domain.model.UploadSessionStatus;
import com.example.demo.domain.repository.UploadSessionRepository;
import com.example.obs.core.ObsMultipartStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 孤儿分片会话清理服务。
 * 扫描创建时间早于阈值的会话：
 * - UPLOADING：先 abort 云端会话（删除已传分片、释放存储），失败保留到下一轮重试（abort 幂等）；
 * - COMPLETED/ABORTED：无云端资源需要处理，直接删行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartSessionCleanupService {

    private final UploadSessionRepository sessionRepository;
    private final ObsMultipartStorage multipartStorage;

    /**
     * 清理创建时间早于 threshold 的会话。
     *
     * @param threshold 阈值（一般传 now - retentionDays）
     * @return 本次清理的会话数
     */
    public int purgeExpired(LocalDateTime threshold) {
        List<UploadSession> sessions = sessionRepository.findBefore(threshold);
        int purged = 0;
        for (UploadSession session : sessions) {
            if (session.getStatus() == UploadSessionStatus.UPLOADING
                    && !abortQuietly(session)) {
                continue;
            }
            sessionRepository.hardDeleteById(session.getId());
            purged++;
        }
        log.info("孤儿分片会话清理完成: threshold={} purged={}", threshold, purged);
        return purged;
    }

    private boolean abortQuietly(UploadSession session) {
        try {
            multipartStorage.abort(session.getStorageKey(), session.getUploadId());
            return true;
        } catch (Exception e) {
            log.warn("分片会话云端终止失败，保留待下轮重试: sessionId={} key={}",
                    session.getId(), session.getStorageKey(), e);
            return false;
        }
    }
}
