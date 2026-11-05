package com.agent.gateway.mcp;

import com.agent.gateway.mcp.config.McpAuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 演示 MCP Server 启动入口。
 * <p>独立进程，默认 {@code http://127.0.0.1:19099/mcp}，由 gateway ADMIN 面登记后发现工具。
 */
@SpringBootApplication
@EnableConfigurationProperties(McpAuthProperties.class)
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
