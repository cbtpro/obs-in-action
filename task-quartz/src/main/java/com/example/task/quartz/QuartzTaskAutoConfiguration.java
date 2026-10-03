package com.example.task.quartz;

import com.example.task.core.TaskScheduler;
import org.quartz.Scheduler;
import org.quartz.spi.JobFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Quartz 任务框架自动装配。
 * 仿照 obs-qiniu 的 AutoConfiguration 模式，
 * 当 classpath 存在 Quartz Scheduler + TaskScheduler 接口时自动注册。
 */
@AutoConfiguration
@ConditionalOnClass({ Scheduler.class, TaskScheduler.class })
public class QuartzTaskAutoConfiguration {

    /**
     * 让 Quartz Job 实例支持 Spring 依赖注入。
     */
    @Bean
    @ConditionalOnMissingBean(JobFactory.class)
    public AutowiringSpringBeanJobFactory springBeanJobFactory(ApplicationContext applicationContext) {
        return new AutowiringSpringBeanJobFactory(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    public TaskScheduler quartzTaskScheduler(Scheduler scheduler, ApplicationContext applicationContext) {
        QuartzTaskScheduler taskScheduler = new QuartzTaskScheduler(scheduler, applicationContext);
        // 设置 JobFactory 让 Quartz 用 Spring 创建 Job 实例
        try {
            scheduler.setJobFactory(springBeanJobFactory(applicationContext));
        } catch (org.quartz.SchedulerException e) {
            throw new RuntimeException("设置 JobFactory 失败", e);
        }
        return taskScheduler;
    }
}
