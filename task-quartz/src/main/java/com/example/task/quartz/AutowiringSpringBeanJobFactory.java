package com.example.task.quartz;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * 让 Quartz Job 实例支持 Spring 依赖注入。
 * 通过 AutowireCapableBeanFactory 自动装配 Job 中的 @Autowired 字段。
 */
public class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory {

    private final ApplicationContext applicationContext;

    public AutowiringSpringBeanJobFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        Object job = super.createJobInstance(bundle);
        applicationContext.getAutowireCapableBeanFactory().autowireBean(job);
        return job;
    }
}
