package com.example.task.quartz;

import com.example.task.core.PageQuery;
import com.example.task.core.PageResult;
import com.example.task.core.TaskContext;
import com.example.task.core.TaskDefinition;
import com.example.task.core.TaskExecutor;
import com.example.task.core.TaskQuery;
import com.example.task.core.TaskScheduler;
import com.example.task.core.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Quartz 调度器实现。
 * 使用 Quartz Scheduler 管理作业注册、触发、停止和状态查询。
 *
 * 持久化设计（配合 JDBC JobStore）：
 * - 任务定义（name/params/cron/executor）随 JobDetail 的 JobDataMap 落库，服务重启后自动恢复
 * - 恢复断点（resumeOffset）与终态快照（status.*）在任务结束时写回 JobDataMap
 * - 全新 trigger 清除断点；resume 保留断点，由执行器从断点继续
 */
@Slf4j
public class QuartzTaskScheduler implements TaskScheduler, InitializingBean {

    private static final String TASK_GROUP = "task-group";

    /** JobDataMap key：任务定义 */
    private static final String KEY_TASK_ID = "taskId";
    private static final String KEY_EXECUTOR_CLASS = "executorClassName";
    private static final String KEY_TASK_NAME = "taskName";
    private static final String KEY_CRON = "cronExpression";
    private static final String PARAM_PREFIX = "param.";

    /** JobDataMap key：恢复断点与终态快照 */
    private static final String KEY_RESUME_OFFSET = "resumeOffset";
    private static final String KEY_STATUS_PROCESSED = "status.processed";
    private static final String KEY_STATUS_TOTAL = "status.total";
    private static final String KEY_STATUS_FAILED = "status.failed";
    private static final String KEY_STATUS_STOP_REQUESTED = "status.stopRequested";
    private static final String KEY_STATUS_MESSAGE = "status.message";

    private final Scheduler scheduler;
    private final ApplicationContext applicationContext;

    /** taskId → 运行中状态（运行时实时更新，终态时落库） */
    private final Map<String, TaskStatus> runtimeStatus = new ConcurrentHashMap<>();

    /** taskId → 任务定义（启动时从 JobStore 恢复） */
    private final Map<String, TaskDefinition> definitions = new ConcurrentHashMap<>();

    /** taskId → 运行中的 TaskContext（stop 时通知 context） */
    private final Map<String, TaskContext> activeContexts = new ConcurrentHashMap<>();

    public QuartzTaskScheduler(Scheduler scheduler, ApplicationContext applicationContext) {
        this.scheduler = scheduler;
        this.applicationContext = applicationContext;
    }

    /**
     * 启动时从 JobStore 恢复任务定义与终态，保证服务重启后任务不丢失。
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        recoverFromJobStore();
    }

    private void recoverFromJobStore() throws SchedulerException {
        for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(TASK_GROUP))) {
            JobDetail jobDetail = scheduler.getJobDetail(jobKey);
            if (jobDetail == null) {
                continue;
            }
            JobDataMap dataMap = jobDetail.getJobDataMap();
            String taskId = dataMap.getString(KEY_TASK_ID);
            if (taskId == null) {
                continue; // 非本调度器管理的作业
            }

            Map<String, Object> params = new HashMap<>();
            for (String key : dataMap.getKeys()) {
                if (key.startsWith(PARAM_PREFIX)) {
                    params.put(key.substring(PARAM_PREFIX.length()), dataMap.get(key));
                }
            }
            TaskDefinition definition = TaskDefinition.builder()
                    .name(dataMap.getString(KEY_TASK_NAME))
                    .cronExpression(dataMap.getString(KEY_CRON))
                    .executorClassName(dataMap.getString(KEY_EXECUTOR_CLASS))
                    .params(params)
                    .build();
            definitions.put(taskId, definition);

            TaskStatus status = new TaskStatus();
            status.setTaskId(taskId);
            status.setName(definition.getName());
            // 重启后必然不在运行，恢复上次落库的终态
            status.setRunning(false);
            status.setStopRequested(dataMap.getBoolean(KEY_STATUS_STOP_REQUESTED));
            status.setProcessed(dataMap.getLong(KEY_STATUS_PROCESSED));
            status.setTotal(dataMap.getLong(KEY_STATUS_TOTAL));
            status.setFailed(dataMap.getLong(KEY_STATUS_FAILED));
            status.setMessage(dataMap.getString(KEY_STATUS_MESSAGE));
            Object mockTag = params.get("mockTag");
            if (mockTag != null) {
                status.setMockTag(mockTag.toString());
            }
            runtimeStatus.put(taskId, status);
            log.info("恢复任务: taskId={}, name={}, processed={}, stopRequested={}",
                    taskId, definition.getName(), status.getProcessed(), status.isStopRequested());
        }
    }

    @Override
    public String register(TaskDefinition definition) {
        String taskId = "task-" + System.currentTimeMillis();
        // 完整定义写入 JobDataMap，随 JobDetail 持久化（JDBC JobStore 重启可恢复）
        JobDataMap dataMap = new JobDataMap();
        dataMap.put(KEY_TASK_ID, taskId);
        dataMap.put(KEY_EXECUTOR_CLASS, definition.getExecutorClassName());
        dataMap.put(KEY_TASK_NAME, definition.getName());
        if (definition.getCronExpression() != null && !definition.getCronExpression().isBlank()) {
            dataMap.put(KEY_CRON, definition.getCronExpression());
        }
        if (definition.getParams() != null) {
            definition.getParams().forEach((k, v) -> dataMap.put(PARAM_PREFIX + k, v));
        }
        JobDetail jobDetail = JobBuilder.newJob(QuartzJobAdapter.class)
                .withIdentity(taskId, TASK_GROUP)
                .usingJobData(dataMap)
                .storeDurably(true)
                .build();

        try {
            scheduler.addJob(jobDetail, true);
            if (definition.getCronExpression() != null && !definition.getCronExpression().isBlank()) {
                Trigger trigger = TriggerBuilder.newTrigger()
                        .withIdentity(taskId + "-trigger", TASK_GROUP)
                        .forJob(jobDetail)
                        .withSchedule(CronScheduleBuilder.cronSchedule(definition.getCronExpression()))
                        .build();
                scheduler.scheduleJob(trigger);
            }
        } catch (SchedulerException e) {
            throw new RuntimeException("注册任务失败: " + definition.getName(), e);
        }

        TaskStatus status = new TaskStatus();
        status.setTaskId(taskId);
        status.setName(definition.getName());
        status.setRunning(false);
        status.setStopRequested(false);
        if (definition.getParams() != null) {
            Object mockTag = definition.getParams().get("mockTag");
            if (mockTag != null) {
                status.setMockTag(mockTag.toString());
            }
        }
        runtimeStatus.put(taskId, status);
        definitions.put(taskId, definition);

        return taskId;
    }

    @Override
    public void trigger(String taskId) {
        // 全新触发：清除持久化的恢复断点，从头开始
        clearResumeOffset(taskId);
        doTrigger(taskId);
    }

    /**
     * 实际触发 Quartz 作业并更新运行状态。
     */
    private void doTrigger(String taskId) {
        try {
            JobKey jobKey = JobKey.jobKey(taskId, TASK_GROUP);
            if (scheduler.checkExists(jobKey)) {
                scheduler.triggerJob(jobKey);
                TaskStatus status = runtimeStatus.get(taskId);
                if (status != null) {
                    status.setRunning(true);
                    status.setStopRequested(false);
                    status.setStartTime(LocalDateTime.now());
                    status.setEndTime(null);
                    status.setMessage(null);
                }
            }
        } catch (SchedulerException e) {
            throw new RuntimeException("触发任务失败: " + taskId, e);
        }
    }

    @Override
    public void stop(String taskId) {
        try {
            // 通知运行中的 context 停止（executor 循环中会检查）
            TaskContext ctx = activeContexts.get(taskId);
            if (ctx != null) {
                ctx.setStopRequested(true);
            }
            scheduler.interrupt(JobKey.jobKey(taskId, TASK_GROUP));
            TaskStatus status = runtimeStatus.get(taskId);
            if (status != null) {
                status.setStopRequested(true);
            }
        } catch (SchedulerException e) {
            throw new RuntimeException("停止任务失败: " + taskId, e);
        }
    }

    @Override
    public void resume(String taskId) {
        TaskStatus status = runtimeStatus.get(taskId);
        if (status == null) {
            throw new IllegalStateException("任务不存在: " + taskId);
        }
        if (status.isRunning()) {
            throw new IllegalStateException("任务正在运行，无需恢复: " + taskId);
        }
        // 断点已持久化在 JobDataMap 的 resumeOffset 中，执行器读取后从断点继续
        doTrigger(taskId);
    }

    @Override
    public TaskStatus getStatus(String taskId) {
        return runtimeStatus.get(taskId);
    }

    @Override
    public PageResult<TaskStatus> list(TaskQuery query) {
        // 关键字模糊匹配：任务 ID / 名称 / 数据标志
        Stream<TaskStatus> stream = runtimeStatus.values().stream();
        String keyword = query.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            stream = stream.filter(s -> containsIgnoreCase(s.getTaskId(), kw)
                    || containsIgnoreCase(s.getName(), kw)
                    || containsIgnoreCase(s.getMockTag(), kw));
        }
        // 稳定排序：新注册的任务（taskId 含时间戳）在前
        List<TaskStatus> matched = stream
                .sorted(Comparator.comparing(TaskStatus::getTaskId).reversed())
                .toList();

        PageQuery page = query.getPage();
        int from = (int) Math.min(page.offset(), matched.size());
        int to = (int) Math.min(from + page.pageSize(), matched.size());
        return new PageResult<>(matched.size(), new ArrayList<>(matched.subList(from, to)));
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword.toLowerCase());
    }

    @Override
    public void remove(String taskId) {
        try {
            JobKey jobKey = JobKey.jobKey(taskId, TASK_GROUP);
            scheduler.deleteJob(jobKey);
            runtimeStatus.remove(taskId);
            definitions.remove(taskId);
        } catch (SchedulerException e) {
            throw new RuntimeException("移除任务失败: " + taskId, e);
        }
    }

    /**
     * 由 QuartzJobAdapter 调用：获取任务定义。
     */
    public TaskDefinition getDefinition(String taskId) {
        return definitions.get(taskId);
    }

    /**
     * 由 QuartzJobAdapter 调用：通过 Spring ApplicationContext 获取执行器 Bean。
     */
    public TaskExecutor getExecutor(Class<?> clazz) {
        return (TaskExecutor) applicationContext.getBean(clazz);
    }

    /**
     * 由 QuartzJobAdapter 调用：注册运行中的 context（stop 时通过它通知 executor）。
     */
    public void registerActiveContext(String taskId, TaskContext context) {
        activeContexts.put(taskId, context);
    }

    /**
     * 由 QuartzJobAdapter 调用：任务结束后移除 context。
     */
    public void unregisterActiveContext(String taskId) {
        activeContexts.remove(taskId);
    }

    /**
     * 由 QuartzJobAdapter 调用：更新运行状态。
     * 运行中仅更新内存；终态（停止/完成/失败）同时落库，供重启恢复。
     */
    public void updateRuntimeStatus(String taskId, TaskStatus status) {
        TaskStatus existing = runtimeStatus.get(taskId);
        if (existing != null) {
            existing.setRunning(status.isRunning());
            existing.setProcessed(status.getProcessed());
            existing.setTotal(status.getTotal());
            existing.setBatchCount(status.getBatchCount());
            existing.setFailed(status.getFailed());
            existing.setMessage(status.getMessage());
            existing.setStopRequested(status.isStopRequested());
            if (status.getStartTime() != null) {
                existing.setStartTime(status.getStartTime());
            }
            if (status.getEndTime() != null) {
                existing.setEndTime(status.getEndTime());
            }
        }
        if (!status.isRunning()) {
            persistFinalStatus(taskId, status);
        }
    }

    /**
     * 终态落库：写入 JobDataMap 持久化。
     * 停止时记录断点（resumeOffset = 已处理量）；完成/失败时清除断点。
     */
    private void persistFinalStatus(String taskId, TaskStatus status) {
        try {
            JobKey jobKey = JobKey.jobKey(taskId, TASK_GROUP);
            JobDetail jobDetail = scheduler.getJobDetail(jobKey);
            if (jobDetail == null) {
                return;
            }
            JobDataMap dataMap = jobDetail.getJobDataMap();
            dataMap.put(KEY_STATUS_PROCESSED, status.getProcessed());
            dataMap.put(KEY_STATUS_TOTAL, status.getTotal());
            dataMap.put(KEY_STATUS_FAILED, status.getFailed());
            dataMap.put(KEY_STATUS_STOP_REQUESTED, status.isStopRequested());
            dataMap.put(KEY_STATUS_MESSAGE, status.getMessage() != null ? status.getMessage() : "");
            if (status.isStopRequested()) {
                dataMap.put(KEY_RESUME_OFFSET, status.getProcessed());
            } else {
                dataMap.remove(KEY_RESUME_OFFSET);
            }
            scheduler.addJob(jobDetail, true);
        } catch (SchedulerException e) {
            // 落库失败不影响任务本身，仅记录日志
            log.warn("任务终态落库失败: taskId={}", taskId, e);
        }
    }

    /**
     * 全新触发前清除持久化的恢复断点。
     */
    private void clearResumeOffset(String taskId) {
        try {
            JobKey jobKey = JobKey.jobKey(taskId, TASK_GROUP);
            JobDetail jobDetail = scheduler.getJobDetail(jobKey);
            if (jobDetail != null && jobDetail.getJobDataMap().containsKey(KEY_RESUME_OFFSET)) {
                jobDetail.getJobDataMap().remove(KEY_RESUME_OFFSET);
                scheduler.addJob(jobDetail, true);
            }
        } catch (SchedulerException e) {
            throw new RuntimeException("清除恢复断点失败: " + taskId, e);
        }
    }
}
