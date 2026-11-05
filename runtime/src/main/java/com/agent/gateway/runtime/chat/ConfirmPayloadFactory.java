package com.agent.gateway.runtime.chat;

import com.agent.gateway.runtime.ConfirmGate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把待确认工具调用挂入 {@link ConfirmGate}，并生成 SSE confirm 事件载荷。
 * <p>所属模块：runtime。echo 与 AgentScope 共用，保证 confirmId 可被 /v1/confirms 接口消费。
 */
public final class ConfirmPayloadFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConfirmPayloadFactory() {
    }

    /**
     * 为一批工具调用各挂一张确认单，并序列化为 confirm 事件 JSON。
     *
     * @param confirmGate 确认闸门
     * @param tenantId 租户 ID
     * @param taskId 任务 ID
     * @param conversationId 会话 ID
     * @param replyId AgentScope 回复 ID；echo 模式可为空
     * @param tools 待确认工具列表（name + argsJson）
     * @return confirm 事件 data 字段（JSON 字符串）
     */
    public static String suspendAndSerialize(
            ConfirmGate confirmGate,
            String tenantId,
            String taskId,
            String conversationId,
            String replyId,
            List<ToolCall> tools) {
        List<Map<String, Object>> calls = new ArrayList<>();
        for (ToolCall tool : tools) {
            // 落库挂起并带上 conversationId，approve 后才能续跑同一会话
            String confirmId = confirmGate.suspend(tenantId, taskId, conversationId, tool.name(), tool.argsJson());
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("confirmId", confirmId);
            call.put("name", tool.name());
            call.put("argsJson", tool.argsJson());
            calls.add(call);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", taskId);
        payload.put("conversationId", conversationId);
        if (replyId != null && !replyId.isBlank()) {
            payload.put("replyId", replyId);
        }
        payload.put("calls", calls);
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("serialize confirm payload failed", e);
        }
    }

    /**
     * 待确认的单次工具调用。
     *
     * @param name 工具名
     * @param argsJson 参数 JSON 文本
     */
    public record ToolCall(String name, String argsJson) {
    }
}
