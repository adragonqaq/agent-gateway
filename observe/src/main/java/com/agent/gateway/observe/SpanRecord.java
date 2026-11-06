package com.agent.gateway.observe;

import java.time.Instant;

/**
 * 持久化的 Trace Span 行。
 * <p>所属模块：observe。
 *
 * @param id Span ID
 * @param tenantId 租户
 * @param traceId 链路 ID
 * @param parentSpanId 父 Span；根为空
 * @param conversationId 会话
 * @param taskId 任务
 * @param name 展示名
 * @param kind 类型
 * @param status 状态
 * @param startedAt 开始
 * @param endedAt 结束；可空
 * @param durationMs 耗时；可空
 * @param attrsJson 附加 JSON；可空
 */
public record SpanRecord(
        String id,
        String tenantId,
        String traceId,
        String parentSpanId,
        String conversationId,
        String taskId,
        String name,
        String kind,
        String status,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        String attrsJson
) {
}
