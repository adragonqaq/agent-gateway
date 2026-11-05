package com.agent.gateway.tools;

/**
 * MCP Server 持久化记录。
 * <p>所属模块：tools。Header 以密文存储，不包含明文鉴权信息。
 *
 * @param id Server 主键
 * @param tenantId 所属租户
 * @param name 展示名
 * @param transportUrl MCP 传输地址
 * @param headersCipher Header JSON 的密文；可为 null
 * @param status 状态，如 ACTIVE / DISABLED
 */
public record McpServerRecord(
        String id,
        String tenantId,
        String name,
        String transportUrl,
        String headersCipher,
        String status
) {
}
