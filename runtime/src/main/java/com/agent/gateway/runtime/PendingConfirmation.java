package com.agent.gateway.runtime;

import java.time.Instant;

/**
 * 待确认（或已决策）的工具调用挂起单。
 * <p>所属模块：runtime。对应 {@code gw_confirm} 表一行。
 *
 * @param id 确认单 ID
 * @param tenantId 租户 ID
 * @param taskId 关联任务 ID
 * @param conversationId 会话 ID，续跑 Agent 时定位状态
 * @param traceId 全链路 ID；续跑时复用
 * @param toolName 待确认工具名
 * @param argsJson 工具参数 JSON
 * @param status 当前决策状态
 * @param expiresAt 过期时间
 */
public record PendingConfirmation(
        String id,
        String tenantId,
        String taskId,
        String conversationId,
        String traceId,
        String toolName,
        String argsJson,
        ConfirmDecision status,
        Instant expiresAt
) {
}
