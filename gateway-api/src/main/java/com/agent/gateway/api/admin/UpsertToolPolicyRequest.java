package com.agent.gateway.api.admin;

/**
 * 写入单条工具策略的请求体。
 *
 * @param toolName 工具名（与 MCP 发现名一致）
 * @param effect ALLOW 或 DENY
 * @param requireConfirm 是否需要人工确认；null 时默认 true
 */
public record UpsertToolPolicyRequest(
        String toolName,
        String effect,
        Boolean requireConfirm
) {
}
