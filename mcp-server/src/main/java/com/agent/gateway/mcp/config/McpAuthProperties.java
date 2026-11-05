package com.agent.gateway.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 演示 MCP 可选 Bearer 鉴权配置。
 * <p>所属模块：mcp-server。token 为空时不启用鉴权，便于本地裸跑；非空时要求
 * {@code Authorization: Bearer <token>}，用于联调 gateway 的 Header 密文登记链路。
 */
@ConfigurationProperties(prefix = "mcp.auth")
public class McpAuthProperties {

    /**
     * 期望的 Bearer Token；空字符串表示关闭鉴权。
     */
    private String token = "";

    /**
     * @return 配置的 token；可能为空
     */
    public String getToken() {
        return token;
    }

    /**
     * @param token Bearer 明文；空则关闭鉴权
     */
    public void setToken(String token) {
        this.token = token == null ? "" : token;
    }

    /**
     * @return 是否启用鉴权
     */
    public boolean enabled() {
        return token != null && !token.isBlank();
    }
}
