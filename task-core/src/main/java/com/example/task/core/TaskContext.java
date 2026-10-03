package com.example.task.core;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 任务执行上下文（调度器传给 TaskExecutor）。
 */
public class TaskContext {

    private final String taskId;
    private final Map<String, Object> params;
    private final Consumer<TaskStatus> statusUpdater;

    private volatile boolean stopRequested = false;
    private long total;
    private long processed;
    private int batchCount;

    public TaskContext(String taskId, Map<String, Object> params, Consumer<TaskStatus> statusUpdater) {
        this.taskId = taskId;
        this.params = params;
        this.statusUpdater = statusUpdater;
    }

    public String getTaskId() {
        return taskId;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public boolean isStopRequested() {
        return stopRequested;
    }

    public void setStopRequested(boolean stopRequested) {
        this.stopRequested = stopRequested;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getProcessed() {
        return processed;
    }

    public int getBatchCount() {
        return batchCount;
    }

    /**
     * 上报进度并更新批次数。
     */
    public void updateProgress(long processed) {
        this.processed = processed;
        this.batchCount++;
        if (statusUpdater != null) {
            TaskStatus status = new TaskStatus();
            status.setTaskId(taskId);
            status.setRunning(true);
            status.setStopRequested(stopRequested);
            status.setTotal(total);
            status.setProcessed(processed);
            status.setBatchCount(batchCount);
            statusUpdater.accept(status);
        }
    }

    /**
     * 上报最终状态（任务结束）。
     */
    public void finish(long processed, long failed, String message) {
        if (statusUpdater != null) {
            TaskStatus status = new TaskStatus();
            status.setTaskId(taskId);
            status.setRunning(false);
            status.setStopRequested(stopRequested);
            status.setTotal(total);
            status.setProcessed(processed);
            status.setFailed(failed);
            status.setBatchCount(batchCount);
            status.setEndTime(java.time.LocalDateTime.now());
            status.setMessage(message);
            statusUpdater.accept(status);
        }
    }

    /**
     * 获取参数值，支持类型转换（JSON 反序列化后 Integer/Long 类型可能不一致）。
     */
    @SuppressWarnings("unchecked")
    public <T> T getParam(String key, T defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        // 数值类型转换：JSON 反序列化后 Integer 需要转 Long
        if (defaultValue instanceof Long && value instanceof Number n) {
            return (T) (Long) n.longValue();
        }
        if (defaultValue instanceof Integer && value instanceof Number n) {
            return (T) (Integer) n.intValue();
        }
        return (T) value;
    }
}
