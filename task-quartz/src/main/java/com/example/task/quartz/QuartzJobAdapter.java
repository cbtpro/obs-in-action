package com.example.task.quartz;

import com.example.task.core.TaskContext;
import com.example.task.core.TaskDefinition;
import com.example.task.core.TaskExecutor;
import com.example.task.core.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.UnableToInterruptJobException;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Quartz Job 适配器：桥接 Quartz Job → TaskExecutor。
 * 实现 InterruptableJob 以支持中途停止。
 * 通过 Spring DI 注入 QuartzTaskScheduler。
 */
@Slf4j
public class QuartzJobAdapter implements InterruptableJob {

    @Autowired
    private QuartzTaskScheduler quartzTaskScheduler;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String taskId = context.getJobDetail().getJobDataMap().getString("taskId");
        String executorClassName = context.getJobDetail().getJobDataMap().getString("executorClassName");

        if (quartzTaskScheduler == null) {
            throw new JobExecutionException("QuartzTaskScheduler 未注入");
        }

        TaskDefinition definition = quartzTaskScheduler.getDefinition(taskId);
        if (definition == null) {
            throw new JobExecutionException("任务定义不存在: " + taskId);
        }

        // 标记运行中
        TaskStatus runningStatus = new TaskStatus();
        runningStatus.setTaskId(taskId);
        runningStatus.setRunning(true);
        runningStatus.setStartTime(LocalDateTime.now());
        quartzTaskScheduler.updateRuntimeStatus(taskId, runningStatus);

        try {
            Class<?> clazz = Class.forName(executorClassName);
            TaskExecutor executor = quartzTaskScheduler.getExecutor(clazz);

            Map<String, Object> definitionParams = definition.getParams() != null
                    ? definition.getParams() : Map.of();
            // 复制 params，避免修改 TaskDefinition 中可能不可变的 Map
            Map<String, Object> params = new HashMap<>(definitionParams);

            // 恢复断点：从持久化的 JobDataMap 读取 offset 并合入 params
            // （mergedJobDataMap 含 JobStore 中的持久化数据，服务重启后断点仍有效）
            Object resumeOffset = context.getMergedJobDataMap().get("resumeOffset");
            if (resumeOffset instanceof Number n && n.longValue() > 0) {
                params.put("offset", n.longValue());
            }

            TaskContext taskContext = new TaskContext(taskId, params, status -> {
                quartzTaskScheduler.updateRuntimeStatus(taskId, status);
            });

            // 设置 total
            Object totalParam = params.get("total");
            if (totalParam instanceof Number n) {
                taskContext.setTotal(n.longValue());
            }

            // 注册运行中 context，让 stop() 能通知到
            quartzTaskScheduler.registerActiveContext(taskId, taskContext);

            try {
                // 执行任务（TaskExecutor 内部通过 context.finish() 上报最终状态）
                executor.execute(taskContext);

                // 如果 executor 没有调用 finish，兜底标记状态
                TaskStatus current = quartzTaskScheduler.getStatus(taskId);
                if (current != null && current.isRunning()) {
                    TaskStatus doneStatus = new TaskStatus();
                    doneStatus.setTaskId(taskId);
                    doneStatus.setRunning(false);
                    doneStatus.setTotal(taskContext.getTotal());
                    doneStatus.setProcessed(taskContext.getProcessed());
                    doneStatus.setEndTime(LocalDateTime.now());
                    // 根据是否被请求停止，区分"已停止"和"完成"
                    doneStatus.setMessage(taskContext.isStopRequested() ? "已停止" : "完成");
                    quartzTaskScheduler.updateRuntimeStatus(taskId, doneStatus);
                }
            } finally {
                quartzTaskScheduler.unregisterActiveContext(taskId);
            }

        } catch (Exception e) {
            log.error("任务执行失败: taskId={}", taskId, e);
            TaskStatus failStatus = new TaskStatus();
            failStatus.setTaskId(taskId);
            failStatus.setRunning(false);
            failStatus.setEndTime(LocalDateTime.now());
            failStatus.setMessage("失败: " + e.getMessage());
            quartzTaskScheduler.updateRuntimeStatus(taskId, failStatus);
            throw new JobExecutionException("任务执行失败: " + taskId, e);
        }
    }

    @Override
    public void interrupt() throws UnableToInterruptJobException {
        // Quartz interrupt 信号，实际停止由 TaskContext.stopRequested 标志控制
    }
}
