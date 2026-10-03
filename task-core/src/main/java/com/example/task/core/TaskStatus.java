package com.example.task.core;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务运行状态（不可变快照）。
 */
@Data
public class TaskStatus {

    private String taskId;
    private String name;
    private boolean running;
    private boolean stopRequested;
    private long total;
    private long processed;
    private long failed;
    private int batchCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String message;

    /** 数据标志（用于清理模拟数据，独立于 message 状态字段） */
    private String mockTag;
}
