package com.agent.gateway.api.admin;

import java.util.Map;

/**
 * 登记 / 更新 MCP Server 的请求体。
 *
 * @param id 已有 Server ID；为空则新建
 * @param name 展示名称
 * @param transportUrl MCP 传输 URL
 * @param headers 明文请求头（可选）；传入则重新加密覆盖
 * @param status ACTIVE / DISABLED；默认 ACTIVE
 */
public record UpsertMcpServerRequest(
        String id,
        String name,
        String transportUrl,
        Map<String, String> headers,
        String status
) {
}
