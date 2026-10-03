package com.example.demo.interfaces.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 供本地代理、容器和外部探针使用的轻量健康检查。 */
@RestController
public class HealthController {

    @GetMapping("/v1/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
