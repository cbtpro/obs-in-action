package com.example.demo.application;

import com.example.demo.domain.model.Candidate;
import com.example.demo.domain.model.FileEntity;
import com.example.demo.domain.repository.CandidateRepository;
import com.example.demo.domain.repository.FileRepository;
import com.example.obs.core.ObsStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 软删除数据的物理清理服务。
 * 扫描打标时间早于阈值的软删记录：先删对象存储对象，再硬删 DB 行；
 * 对象删除失败的记录保留到下一轮重试（两侧删除均幂等：七牛 612 与阿里云删除不存在对象都视为成功）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileCleanupService {

    private final FileRepository fileRepository;
    private final CandidateRepository candidateRepository;
    private final ObsStorage obsStorage;

    /**
     * 清理软删除时间早于 threshold 的文件与候选人记录。
     *
     * @param threshold 阈值（一般传 now - retentionDays）
     * @return 本次物理清理的记录数
     */
    public int purgeExpired(LocalDateTime threshold) {
        int purged = 0;
        for (FileEntity file : fileRepository.findDeletedBefore(threshold)) {
            if (purgeObject(file.getStorageKey(), "file")) {
                fileRepository.hardDeleteByStorageKey(file.getStorageKey());
                purged++;
            }
        }
        for (Candidate candidate : candidateRepository.findDeletedBefore(threshold)) {
            // 照片与简历任一删除失败则整行保留，下轮重试（对象删除幂等，重复执行安全）
            if (purgeObject(candidate.getPhotoKey(), "candidate.photo")
                    && purgeObject(candidate.getResumeKey(), "candidate.resume")) {
                candidateRepository.hardDeleteById(candidate.getId());
                purged++;
            }
        }
        log.info("物理清理完成: threshold={} purged={}", threshold, purged);
        return purged;
    }

    private boolean purgeObject(String key, String source) {
        if (key == null || key.isBlank()) {
            return true;
        }
        try {
            obsStorage.delete(key);
            return true;
        } catch (Exception e) {
            log.warn("物理清理对象删除失败，记录保留待下轮重试: source={} key={}", source, key, e);
            return false;
        }
    }
}
