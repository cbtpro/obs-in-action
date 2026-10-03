package com.example.task.core;

/**
 * 任务执行器：业务逻辑实现此接口，由调度器回调。
 */
public interface TaskExecutor {

    /**
     * 执行任务，通过 context 上报进度和检查停止标志。
     */
    void execute(TaskContext context);
}
