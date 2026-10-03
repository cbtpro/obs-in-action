package com.example.task.core;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 任务定义（注册时传入）。
 */
@Data
@Builder
public class TaskDefinition {

    /** 任务名称 */
    private String name;

    /** 定时表达式（可选，为空则仅手动触发） */
    private String cronExpression;

    /** 任务参数（total, batchSize, intervalMillis, mockTag 等） */
    private Map<String, Object> params;

    /** 执行器类名（全限定名，调度器通过反射实例化） */
    private String executorClassName;
}
