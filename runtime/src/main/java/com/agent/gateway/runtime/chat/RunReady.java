package com.agent.gateway.runtime.chat;

/**
 * 任务就绪回调载荷：SSE meta 所需的 taskId + traceId。
 * <p>所属模块：runtime。
 *
 * @param taskId 本段任务 ID
 * @param traceId 全链路 ID
 */
public record RunReady(String taskId, String traceId) {
}
