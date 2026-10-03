package com.example.task.quartz;

import com.example.task.core.PageResult;
import com.example.task.core.TaskDefinition;
import com.example.task.core.TaskQuery;
import com.example.task.core.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.springframework.context.ApplicationContext;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * QuartzTaskScheduler.list 关键字过滤与分页测试。
 */
class QuartzTaskSchedulerListTest {

    private QuartzTaskScheduler taskScheduler;

    @BeforeEach
    void setUp() throws Exception {
        Scheduler scheduler = mock(Scheduler.class);
        // JobStore 为空（无历史任务需要恢复）
        when(scheduler.getJobKeys(any())).thenReturn(new HashSet<JobKey>());
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        taskScheduler = new QuartzTaskScheduler(scheduler, applicationContext);
        taskScheduler.afterPropertiesSet();
    }

    private String register(String name, String mockTag) throws InterruptedException {
        // taskId 基于毫秒时间戳，同毫秒注册会覆盖，间隔 2ms 保证唯一
        Thread.sleep(2);
        return taskScheduler.register(TaskDefinition.builder()
                .name(name)
                .executorClassName("com.example.FakeExecutor")
                .params(Map.of("mockTag", mockTag))
                .build());
    }

    @Test
    void shouldReturnAllWhenKeywordAbsent() throws InterruptedException {
        register("数据播种A", "MOCK_1");
        register("数据播种B", "MOCK_2");
        register("文件清理", "MOCK_3");

        PageResult<TaskStatus> result = taskScheduler.list(TaskQuery.builder().build());

        assertEquals(3, result.total());
        assertEquals(3, result.records().size());
    }

    @Test
    void shouldFilterByKeywordOnName() throws InterruptedException {
        register("数据播种A", "MOCK_1");
        register("数据播种B", "MOCK_2");
        register("文件清理", "MOCK_3");

        PageResult<TaskStatus> result = taskScheduler.list(
                TaskQuery.builder().keyword("播种").build());

        assertEquals(2, result.total());
        assertTrue(result.records().stream().allMatch(s -> s.getName().contains("播种")));
    }

    @Test
    void shouldFilterByKeywordOnMockTagCaseInsensitively() throws InterruptedException {
        register("数据播种A", "MOCK_alpha");
        register("数据播种B", "MOCK_beta");

        PageResult<TaskStatus> result = taskScheduler.list(
                TaskQuery.builder().keyword("mock_ALPHA").build());

        assertEquals(1, result.total());
        assertEquals("MOCK_alpha", result.records().get(0).getMockTag());
    }

    @Test
    void shouldFilterByKeywordOnTaskId() throws InterruptedException {
        String taskId = register("数据播种A", "MOCK_1");
        register("数据播种B", "MOCK_2");

        PageResult<TaskStatus> result = taskScheduler.list(
                TaskQuery.builder().keyword(taskId).build());

        assertEquals(1, result.total());
        assertEquals(taskId, result.records().get(0).getTaskId());
    }

    @Test
    void shouldPaginateAndSortByTaskIdDesc() throws InterruptedException {
        String first = register("任务1", "MOCK_1");
        String second = register("任务2", "MOCK_2");
        String third = register("任务3", "MOCK_3");

        // 第 1 页 2 条：按 taskId 倒序（新注册在前）
        PageResult<TaskStatus> page1 = taskScheduler.list(TaskQuery.builder()
                .page(com.example.task.core.PageQuery.of(1, 2)).build());
        assertEquals(3, page1.total());
        assertEquals(2, page1.records().size());
        assertEquals(third, page1.records().get(0).getTaskId());
        assertEquals(second, page1.records().get(1).getTaskId());

        // 第 2 页 1 条
        PageResult<TaskStatus> page2 = taskScheduler.list(TaskQuery.builder()
                .page(com.example.task.core.PageQuery.of(2, 2)).build());
        assertEquals(3, page2.total());
        assertEquals(List.of(first), page2.records().stream().map(TaskStatus::getTaskId).toList());

        // 超出范围的页码返回空页但 total 正确
        PageResult<TaskStatus> page9 = taskScheduler.list(TaskQuery.builder()
                .page(com.example.task.core.PageQuery.of(9, 2)).build());
        assertEquals(3, page9.total());
        assertTrue(page9.records().isEmpty());
    }
}
