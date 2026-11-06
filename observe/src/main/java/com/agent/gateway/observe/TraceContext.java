package com.agent.gateway.observe;

/**
 * 当前执行线程绑定的链路上下文。
 * <p>所属模块：observe。
 *
 * @param tenantId 租户 ID
 * @param conversationId 会话 ID
 * @param traceId 全链路 ID
 * @param taskId 本段任务 ID
 * @param runSpanId 本段 RUN span ID（工具/确认的默认父节点）
 */
public record TraceContext(
        String tenantId,
        String conversationId,
        String traceId,
        String taskId,
        String runSpanId
) {
}
