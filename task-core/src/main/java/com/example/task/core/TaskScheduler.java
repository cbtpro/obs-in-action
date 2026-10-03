package com.example.task.core;

/**
 * 任务调度器抽象：注册、触发、停止、查询。
 * 类似 ObsStorage，由 task-quartz 等模块提供实现。
 */
public interface TaskScheduler {

    /**
     * 注册任务定义，返回 taskId。
     *
     * @param definition 任务定义（名称、参数、定时表达式、执行器类）
     * @return 任务 ID
     */
    String register(TaskDefinition definition);

    /**
     * 立即触发任务执行。
     */
    void trigger(String taskId);

    /**
     * 请求停止正在运行的任务（记录断点，可恢复）。
     */
    void stop(String taskId);

    /**
     * 恢复（继续）已停止的任务，从断点处继续执行。
     */
    void resume(String taskId);

    /**
     * 查询任务运行状态。
     */
    TaskStatus getStatus(String taskId);

    /**
     * 按条件分页列出已注册任务。
     *
     * @param query 查询条件（关键字模糊匹配 + 分页参数）
     * @return 分页结果
     */
    PageResult<TaskStatus> list(TaskQuery query);

    /**
     * 移除任务定义（停止运行中的任务并删除注册）。
     */
    void remove(String taskId);
}
