package com.example.taskservice.job;

import com.example.task.core.TaskContext;
import com.example.task.core.TaskExecutor;
import com.example.taskservice.persistence.CandidateMapper;
import com.example.taskservice.persistence.CandidateRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据播种任务执行器。
 * 从 CandidateApplicationService 迁移，分批生成 + 插入，支持中途停止和进度上报。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeedJob implements TaskExecutor {

    private final CandidateMapper candidateMapper;

    private static final String[] SURNAMES = {
            "赵", "钱", "孙", "李", "周", "吴", "郑", "王",
            "冯", "陈", "褚", "卫", "蒋", "沈", "韩", "杨",
            "朱", "秦", "尤", "许", "何", "吕", "施", "张"
    };

    @Override
    public void execute(TaskContext context) {
        long total = context.getParam("total", 100000L);
        int batchSize = context.getParam("batchSize", 10000);
        long intervalMillis = context.getParam("intervalMillis", 0L);
        String mockTag = context.getParam("mockTag", "MOCK_DEFAULT");
        // 断点续传：从已处理量继续，新任务 offset=0
        long startOffset = context.getParam("offset", 0L);

        context.setTotal(total);
        log.info("开始播种: total={}, batchSize={}, intervalMillis={}, mockTag={}, startOffset={}",
                total, batchSize, intervalMillis, mockTag, startOffset);

        long processed = startOffset;
        long failed = 0;
        int batchCount = 0;

        for (long offset = startOffset; offset < total; offset += batchSize) {
            if (context.isStopRequested()) {
                log.info("任务被请求停止，已处理 {} 条", processed);
                context.finish(processed, failed, "已停止");
                return;
            }

            int size = (int) Math.min(batchSize, total - offset);
            try {
                List<CandidateRecord> batch = generateBatch(offset, size, mockTag);
                // 分段插入，避免单条 SQL 过大
                int chunkSize = 500;
                for (int i = 0; i < batch.size(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, batch.size());
                    candidateMapper.batchInsert(batch.subList(i, end));
                }
                processed += size;
                batchCount++;
                context.updateProgress(processed);
                log.info("批次 {} 完成: processed={}/{}", batchCount, processed, total);

                if (intervalMillis > 0) {
                    Thread.sleep(intervalMillis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                context.finish(processed, failed, "已停止");
                return;
            } catch (Exception e) {
                failed += size;
                log.error("批次 {} 失败: offset={}, size={}", batchCount + 1, offset, size, e);
            }
        }

        context.finish(processed, failed, "完成");
        log.info("播种完成: total={}, processed={}, failed={}", total, processed, failed);
    }

    /**
     * 生成一批模拟候选人数据。
     * 通过分解全局序号为 id_card 各组成部分，保证唯一性：
     * 区域码(1000) × 年(50) × 月(12) × 日(28) × 序号(1000) = 16.8 亿
     */
    private List<CandidateRecord> generateBatch(long offset, int count, String mockTag) {
        LocalDateTime now = LocalDateTime.now();
        List<CandidateRecord> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long idx = offset + i + 1;
            int seq = (int) (idx % 1000);
            int day = 1 + (int) ((idx / 1000) % 28);
            int month = 1 + (int) ((idx / 28000) % 12);
            int year = 1960 + (int) ((idx / 336000) % 50);
            int regionCode = 110000 + (int) ((idx / 16800000) % 1000);
            int age = 2026 - year;

            String idCard = String.format("%06d", regionCode)
                    + String.format("%04d%02d%02d", year, month, day)
                    + String.format("%03d", seq)
                    + (idx % 2 == 0 ? "X" : String.valueOf(idx % 10));

            CandidateRecord record = new CandidateRecord();
            record.setName(SURNAMES[(int) (idx % SURNAMES.length)] + "测试" + idx);
            record.setIdCard(idCard);
            record.setGender(idx % 2 == 0 ? "女" : "男");
            record.setAge(age);
            record.setBio("模拟候选人 #" + idx);
            record.setMockTag(mockTag);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            list.add(record);
        }
        return list;
    }
}
