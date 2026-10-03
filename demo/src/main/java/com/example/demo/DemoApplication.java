package com.example.demo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DDD 业务应用启动类。
 * 只依赖 obs-core 接口，底层存储实现（七牛/阿里云）由 AutoConfiguration 按 obs.provider 自动装配。
 */
@SpringBootApplication
@MapperScan("com.example.demo.infrastructure.persistence.mybatis")
@ConfigurationPropertiesScan
@EnableScheduling
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
