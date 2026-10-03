package com.example.taskservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 任务管理微服务启动类。
 * 依赖 task-core 抽象层，task-quartz 提供的 AutoConfiguration 自动装配 QuartzTaskScheduler。
 */
@SpringBootApplication
@MapperScan("com.example.taskservice.persistence")
public class TaskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskServiceApplication.class, args);
    }
}
