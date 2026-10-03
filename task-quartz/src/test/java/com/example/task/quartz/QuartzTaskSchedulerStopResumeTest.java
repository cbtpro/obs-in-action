package com.example.task.quartz;

import com.example.task.core.TaskContext;
import com.example.task.core.TaskDefinition;
import com.example.task.core.TaskExecutor;
import com.example.task.core.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.spi.JobFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 停止/继续断点全流程测试。
 * 使用真实 Quartz（RAM JobStore）验证：触发 → 停止（断点落 JobDataMap）→ 继续（从断点执行）→ 完成（断点清除）。
 */
class QuartzTaskSchedulerStopResumeTest {

    private Scheduler scheduler;
    private QuartzTaskScheduler taskScheduler;
    private ControllableExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        // 内存 JobStore 的真实 Quartz，无需数据库
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "StopResumeTestScheduler");
        props.setProperty("org.quartz.threadPool.threadCount", "2");
        scheduler = new StdSchedulerFactory(props).getScheduler();
        scheduler.start();

        executor = new ControllableExecutor();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(ControllableExecutor.class)).thenReturn(executor);

        taskScheduler = new QuartzTaskScheduler(scheduler, applicationContext);
        taskScheduler.afterPropertiesSet();

        // Quartz 默认 JobFactory 不走 Spring DI，手工装配 adapter 的依赖
        scheduler.setJobFactory((bundle, sched) -> {
            QuartzJobAdapter adapter = new QuartzJobAdapter();
            ReflectionTestUtils.setField(adapter, "quartzTaskScheduler", taskScheduler);
            return adapter;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        scheduler.shutdown(true);
    }

    private String registerTask(long total, long batchSize) throws InterruptedException {
        Thread.sleep(2); // taskId 基于毫秒时间戳，避免同毫秒冲突
        return taskScheduler.register(TaskDefinition.builder()
                .name("断点测试")
                .executorClassName(ControllableExecutor.class.getName())
                .params(Map.of("total", total, "batchSize", batchSize, "mockTag", "MOCK_TEST"))
                .build());
    }

    /** 轮询等待任务状态满足条件 */
    private TaskStatus awaitStatus(String taskId, Predicate<TaskStatus> cond) {
        return await().atMost(10, TimeUnit.SECONDS).pollInterval(50, TimeUnit.MILLISECONDS)
                .until(() -> taskScheduler.getStatus(taskId), cond::test);
    }

    @Test
    void stopShouldPersistOffsetAndResumeShouldContinueFromBreakpoint() throws Exception {
        String taskId = registerTask(100, 10);
        taskScheduler.trigger(taskId);

        // 等处理过半后请求停止
        awaitStatus(taskId, s -> s.getProcessed() >= 20);
        taskScheduler.stop(taskId);

        // 终态：已停止 + 断点落库
        TaskStatus stopped = awaitStatus(taskId, s -> !s.isRunning());
        assertTrue(stopped.isStopRequested());
        assertEquals("已停止", stopped.getMessage());
        long breakpoint = stopped.getProcessed();
        assertTrue(breakpoint >= 20 && breakpoint < 100);
        assertEquals(breakpoint, jobDataMap(taskId).get("resumeOffset"));

        // 继续：从断点恢复执行直到完成
        taskScheduler.resume(taskId);
        TaskStatus done = awaitStatus(taskId, s -> !s.isRunning() && !s.isStopRequested());
        assertEquals(100, done.getProcessed());
        assertEquals("完成", done.getMessage());
        // 断点起点 = 停止时的已处理量
        assertEquals(breakpoint, executor.lastStartOffset.get());
        // 完成后断点清除
        assertFalse(jobDataMap(taskId).containsKey("resumeOffset"));
    }

    @Test
    void freshTriggerShouldRestartFromZeroAfterStop() throws Exception {
        String taskId = registerTask(100, 10);
        taskScheduler.trigger(taskId);

        awaitStatus(taskId, s -> s.getProcessed() >= 10);
        taskScheduler.stop(taskId);
        TaskStatus stopped = awaitStatus(taskId, s -> !s.isRunning());
        assertTrue(stopped.getProcessed() >= 10);

        // 全新触发（非 resume）：清除断点，从头执行
        taskScheduler.trigger(taskId);
        TaskStatus done = awaitStatus(taskId, s -> !s.isRunning() && !s.isStopRequested());
        assertEquals(100, done.getProcessed());
        assertEquals(0, executor.lastStartOffset.get());
        assertFalse(jobDataMap(taskId).containsKey("resumeOffset"));
    }

    @Test
    void resumeShouldRejectUnknownAndRunningTask() throws Exception {
        assertThrows(IllegalStateException.class, () -> taskScheduler.resume("task-not-exist"));

        String taskId = registerTask(100, 5);
        taskScheduler.trigger(taskId);
        awaitStatus(taskId, s -> s.getProcessed() >= 5);
        assertThrows(IllegalStateException.class, () -> taskScheduler.resume(taskId));
        taskScheduler.stop(taskId);
        awaitStatus(taskId, s -> !s.isRunning());
    }

    private org.quartz.JobDataMap jobDataMap(String taskId) throws Exception {
        JobDetail jobDetail = scheduler.getJobDetail(JobKey.jobKey(taskId, "task-group"));
        return jobDetail.getJobDataMap();
    }

    /**
     * 可控执行器：按 batchSize 递增 processed，检查 stopRequested 实现可中断。
     */
    static class ControllableExecutor implements TaskExecutor {

        final AtomicLong lastStartOffset = new AtomicLong(-1);

        @Override
        public void execute(TaskContext context) {
            long total = context.getParam("total", 0L);
            long batchSize = context.getParam("batchSize", 10L);
            long offset = context.getParam("offset", 0L);
            lastStartOffset.set(offset);
            context.setTotal(total);

            long processed = offset;
            while (processed < total) {
                if (context.isStopRequested()) {
                    context.finish(processed, 0, "已停止");
                    return;
                }
                processed = Math.min(processed + batchSize, total);
                context.updateProgress(processed);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    context.finish(processed, 0, "已停止");
                    return;
                }
            }
            context.finish(processed, 0, "完成");
        }
    }
}
