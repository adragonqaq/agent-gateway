package com.agent.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Agent Gateway 启动入口。
 * 扫描 {@code com.agent.gateway} 包以装配 API、运行时与租户等模块；开启定时任务（确认过期扫描）。
 */
@SpringBootApplication(scanBasePackages = "com.agent.gateway")
@EnableScheduling
public class GatewayApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
