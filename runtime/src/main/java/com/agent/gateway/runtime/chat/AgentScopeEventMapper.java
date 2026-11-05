package com.agent.gateway.runtime.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultState;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 将 AgentScope 原生事件映射为网关 {@link AgentEvent}。
 * <p>所属模块：runtime。无法识别的事件丢弃，避免把内部事件泄漏给调用方。
 * 工具生命周期 JSON 由本类组装；耗时由 {@link ToolCallTracker} 注入。
 */
final class AgentScopeEventMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 工具类，禁止实例化。
     */
    private AgentScopeEventMapper() {
    }

    /**
     * 映射单条 AgentScope 事件（非流式：允许 AgentResult 作为完整 answer）。
     *
     * @param event 原生事件
     * @return 可对外暴露的事件；无法识别时为空
     */
    static Optional<AgentEvent> map(io.agentscope.core.event.AgentEvent event) {
        return map(event, false);
    }

    /**
     * 映射单条 AgentScope 事件。
     *
     * @param event 原生事件
     * @param textDeltaAlreadyEmitted 本轮是否已下发过 TextBlockDelta；为 true 时跳过
     *                                AgentResult 全文，避免前端把增量与全文再拼一遍
     * @return 可对外暴露的事件；无法识别时为空
     */
    static Optional<AgentEvent> map(io.agentscope.core.event.AgentEvent event, boolean textDeltaAlreadyEmitted) {
        if (event instanceof ThinkingBlockDeltaEvent e) {
            return Optional.of(new AgentEvent("thinking", nullToEmpty(e.getDelta())));
        }
        if (event instanceof TextBlockDeltaEvent e) {
            return Optional.of(new AgentEvent("answer", nullToEmpty(e.getDelta())));
        }
        if (event instanceof ToolCallStartEvent e) {
            return Optional.of(toolStart(e.getToolCallId(), e.getToolCallName()));
        }
        if (event instanceof ToolCallDeltaEvent e) {
            // 单测/直连映射：仅带本片 delta；执行器侧会累积后覆盖 argsJson
            return Optional.of(toolArgs(e.getToolCallId(), e.getToolCallName(), nullToEmpty(e.getDelta())));
        }
        if (event instanceof ToolResultEndEvent e) {
            return Optional.of(toolEnd(
                    e.getToolCallId(),
                    e.getToolCallName(),
                    mapToolStatus(e.getState()),
                    null,
                    null));
        }
        if (event instanceof RequireUserConfirmEvent e) {
            String tools = e.getToolCalls() == null ? "" : e.getToolCalls().stream()
                    .map(t -> t.getName())
                    .collect(Collectors.joining(","));
            return Optional.of(new AgentEvent("confirm", tools));
        }
        if (event instanceof AgentResultEvent e) {
            if (textDeltaAlreadyEmitted) {
                return Optional.empty();
            }
            return Optional.of(new AgentEvent("answer", extractText(e.getResult())));
        }
        return Optional.empty();
    }

    /**
     * 工具开始：{@code phase=start}。
     */
    static AgentEvent toolStart(String toolCallId, String name) {
        ObjectNode node = JSON.createObjectNode();
        node.put("phase", "start");
        node.put("toolCallId", nullToEmpty(toolCallId));
        node.put("name", nullToEmpty(name));
        return new AgentEvent("tool", write(node));
    }

    /**
     * 工具参数更新：{@code phase=args}。
     */
    static AgentEvent toolArgs(String toolCallId, String name, String argsJson) {
        ObjectNode node = JSON.createObjectNode();
        node.put("phase", "args");
        node.put("toolCallId", nullToEmpty(toolCallId));
        node.put("name", nullToEmpty(name));
        if (argsJson != null && !argsJson.isEmpty()) {
            node.put("argsJson", argsJson);
        }
        return new AgentEvent("tool", write(node));
    }

    /**
     * 工具结束：{@code phase=end}；可带耗时与最终参数。
     */
    static AgentEvent toolEnd(
            String toolCallId,
            String name,
            String status,
            Long durationMs,
            String argsJson) {
        ObjectNode node = JSON.createObjectNode();
        node.put("phase", "end");
        node.put("toolCallId", nullToEmpty(toolCallId));
        node.put("name", nullToEmpty(name));
        node.put("status", nullToEmpty(status));
        if (durationMs != null) {
            node.put("durationMs", durationMs);
        }
        if (argsJson != null && !argsJson.isEmpty()) {
            node.put("argsJson", argsJson);
        }
        return new AgentEvent("tool", write(node));
    }

    /**
     * 本轮收口：{@code {"status":"ok|error","elapsedMs":n}}。
     */
    static AgentEvent done(String status, long elapsedMs) {
        ObjectNode node = JSON.createObjectNode();
        node.put("status", status);
        node.put("elapsedMs", Math.max(0L, elapsedMs));
        return new AgentEvent("done", write(node));
    }

    /**
     * AgentScope 工具结果状态 → 对外 status。
     */
    static String mapToolStatus(ToolResultState state) {
        if (state == null) {
            return "ok";
        }
        return switch (state) {
            case SUCCESS, RUNNING -> "ok";
            case ERROR, DENIED -> "error";
            case INTERRUPTED -> "interrupted";
        };
    }

    private static String write(ObjectNode node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) {
            return "";
        }
        return msg.getContent().stream()
                .filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast)
                .map(TextBlock::getText)
                .collect(Collectors.joining());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
