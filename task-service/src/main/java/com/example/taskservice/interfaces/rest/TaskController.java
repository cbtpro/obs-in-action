package com.example.taskservice.interfaces.rest;

import com.example.task.core.PageQuery;
import com.example.task.core.PageResult;
import com.example.task.core.TaskDefinition;
import com.example.task.core.TaskQuery;
import com.example.task.core.TaskScheduler;
import com.example.task.core.TaskStatus;
import com.example.taskservice.job.DataSeedJob;
import com.example.taskservice.persistence.CandidateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 任务管理 REST API。
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskScheduler taskScheduler;
    private final CandidateMapper candidateMapper;

    /**
     * 注册播种任务。
     */
    @PostMapping("/seed")
    public ResponseEntity<TaskStatus> registerSeed(@RequestBody SeedRequest request) {
        String mockTag = request.getMockTag();
        if (mockTag == null || mockTag.isBlank()) {
            mockTag = "MOCK_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        }

        TaskDefinition definition = TaskDefinition.builder()
                .name(request.getName() != null ? request.getName() : "数据播种")
                .cronExpression(request.getCronExpression())
                .executorClassName(DataSeedJob.class.getName())
                .params(Map.of(
                        "total", request.getTotal(),
                        "batchSize", request.getBatchSize(),
                        "intervalMillis", request.getIntervalMillis(),
                        "mockTag", mockTag
                ))
                .build();

        String taskId = taskScheduler.register(definition);
        return ResponseEntity.ok(taskScheduler.getStatus(taskId));
    }

    /**
     * 立即触发任务。
     */
    @PostMapping("/{taskId}/trigger")
    public ResponseEntity<Void> trigger(@PathVariable String taskId) {
        taskScheduler.trigger(taskId);
        return ResponseEntity.ok().build();
    }

    /**
     * 停止正在运行的任务（记录断点，可恢复）。
     */
    @PostMapping("/{taskId}/stop")
    public ResponseEntity<Void> stop(@PathVariable String taskId) {
        taskScheduler.stop(taskId);
        return ResponseEntity.ok().build();
    }

    /**
     * 恢复（继续）已停止的任务，从断点处继续执行。
     */
    @PostMapping("/{taskId}/resume")
    public ResponseEntity<Void> resume(@PathVariable String taskId) {
        taskScheduler.resume(taskId);
        return ResponseEntity.ok().build();
    }

    /**
     * 查询任务状态。
     */
    @GetMapping("/{taskId}/status")
    public ResponseEntity<TaskStatus> getStatus(@PathVariable String taskId) {
        return ResponseEntity.ok(taskScheduler.getStatus(taskId));
    }

    /**
     * 按条件分页列出任务。
     * 查询条件通过 POST 请求体传递，支持关键字模糊匹配任务 ID / 名称 / 数据标志。
     */
    @PostMapping("/list")
    public ResponseEntity<PageResult<TaskStatus>> list(@RequestBody TaskListRequest request) {
        TaskQuery query = TaskQuery.builder()
                .keyword(request.getKeyword())
                .page(PageQuery.of(
                        request.getPage() != null ? request.getPage() : 1,
                        request.getPageSize() != null ? request.getPageSize() : 10))
                .build();
        return ResponseEntity.ok(taskScheduler.list(query));
    }

    /**
     * 移除任务定义。
     */
    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> remove(@PathVariable String taskId) {
        taskScheduler.remove(taskId);
        return ResponseEntity.ok().build();
    }

    /**
     * 按数据标志清理模拟数据。
     */
    @DeleteMapping("/mock-data/{mockTag}")
    public ResponseEntity<Map<String, Object>> clearMockData(@PathVariable String mockTag) {
        int deleted = candidateMapper.deleteByMockTag(mockTag);
        return ResponseEntity.ok(Map.of("deleted", deleted, "mockTag", mockTag));
    }

    /**
     * 按数据标志统计条数。
     */
    @GetMapping("/mock-data/{mockTag}/count")
    public ResponseEntity<Map<String, Object>> countMockData(@PathVariable String mockTag) {
        long count = candidateMapper.countByMockTag(mockTag);
        return ResponseEntity.ok(Map.of("count", count, "mockTag", mockTag));
    }

    @lombok.Data
    public static class SeedRequest {
        private String name;
        private long total = 100000;
        private int batchSize = 10000;
        private long intervalMillis = 0;
        private String cronExpression;
        private String mockTag;
    }
}
