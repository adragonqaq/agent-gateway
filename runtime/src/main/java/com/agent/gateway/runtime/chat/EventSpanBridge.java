package com.agent.gateway.runtime.chat;

import com.agent.gateway.observe.SpanKind;
import com.agent.gateway.observe.SpanRecorder;
import com.agent.gateway.observe.SpanStatus;
import com.agent.gateway.observe.TraceContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 根据对外 SSE 事件同步写入 TOOL / CONFIRM / LLM Span。
 * <p>所属模块：runtime。挂在 {@link AgentChatService} 事件 sink 上，不侵入执行器内部。
 */
final class EventSpanBridge {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final SpanRecorder spanRecorder;
    private final TraceContext ctx;
    private final Map<String, String> toolSpans = new ConcurrentHashMap<>();
    private String llmSpanId;
    private String confirmSpanId;

    EventSpanBridge(SpanRecorder spanRecorder, TraceContext ctx) {
        this.spanRecorder = spanRecorder;
        this.ctx = ctx;
    }

    /**
     * 包装下游 sink：先记 span，再转发。
     */
    Consumer<AgentEvent> wrap(Consumer<AgentEvent> downstream) {
        return event -> {
            try {
                onEvent(event);
            } catch (RuntimeException ignored) {
                // 观测失败不阻断对话
            }
            downstream.accept(event);
        };
    }

    /**
     * 本轮结束时收口未结束的 LLM / CONFIRM。
     */
    void finish(SpanStatus runStatus) {
        if (llmSpanId != null) {
            spanRecorder.end(ctx.tenantId(), llmSpanId, runStatus == SpanStatus.ERROR ? SpanStatus.ERROR : SpanStatus.OK, null);
            llmSpanId = null;
        }
        if (confirmSpanId != null && runStatus != SpanStatus.WAITING) {
            // 仍 WAITING 的 confirm 留给续跑或审批收口
            if (runStatus == SpanStatus.CANCELLED) {
                spanRecorder.cancel(ctx.tenantId(), confirmSpanId);
                confirmSpanId = null;
            }
        }
    }

    /**
     * 审批续跑时结束挂起的 CONFIRM span。
     */
    void settleConfirm(boolean approved) {
        if (confirmSpanId != null) {
            spanRecorder.end(
                    ctx.tenantId(),
                    confirmSpanId,
                    approved ? SpanStatus.OK : SpanStatus.CANCELLED,
                    "{\"approved\":" + approved + "}");
            confirmSpanId = null;
        }
    }

    private void onEvent(AgentEvent event) {
        if (event == null || event.type() == null) {
            return;
        }
        switch (event.type()) {
            case "answer", "thinking" -> ensureLlm();
            case "tool" -> onTool(event.data());
            case "confirm" -> onConfirm(event.data());
            default -> {
            }
        }
    }

    private void ensureLlm() {
        if (llmSpanId != null) {
            return;
        }
        llmSpanId = spanRecorder.start(
                ctx.tenantId(),
                ctx.traceId(),
                ctx.runSpanId(),
                ctx.conversationId(),
                ctx.taskId(),
                "llm",
                SpanKind.LLM,
                SpanStatus.OK,
                null);
    }

    private void onTool(String data) {
        JsonNode node = parse(data);
        if (node == null) {
            return;
        }
        String phase = text(node, "phase");
        String toolCallId = text(node, "toolCallId");
        String name = text(node, "name");
        if ("start".equals(phase)) {
            String spanId = spanRecorder.start(
                    ctx.tenantId(),
                    ctx.traceId(),
                    ctx.runSpanId(),
                    ctx.conversationId(),
                    ctx.taskId(),
                    name.isEmpty() ? "tool" : name,
                    SpanKind.TOOL,
                    SpanStatus.OK,
                    null);
            if (!toolCallId.isEmpty()) {
                toolSpans.put(toolCallId, spanId);
            }
            return;
        }
        if ("end".equals(phase)) {
            String spanId = toolCallId.isEmpty() ? null : toolSpans.remove(toolCallId);
            if (spanId == null) {
                return;
            }
            String status = text(node, "status");
            SpanStatus spanStatus = "error".equals(status) || "interrupted".equals(status)
                    ? SpanStatus.ERROR
                    : SpanStatus.OK;
            String args = text(node, "argsJson");
            String attrs = args.isEmpty() ? null : "{\"argsJson\":" + quote(args) + "}";
            spanRecorder.end(ctx.tenantId(), spanId, spanStatus, attrs);
        }
    }

    private void onConfirm(String data) {
        if (confirmSpanId != null) {
            return;
        }
        confirmSpanId = spanRecorder.start(
                ctx.tenantId(),
                ctx.traceId(),
                ctx.runSpanId(),
                ctx.conversationId(),
                ctx.taskId(),
                "confirm",
                SpanKind.CONFIRM,
                SpanStatus.WAITING,
                data == null || data.isBlank() ? null : "{\"payload\":" + quote(data) + "}");
    }

    private static JsonNode parse(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(data);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    private static String quote(String raw) {
        try {
            return JSON.writeValueAsString(raw);
        } catch (Exception e) {
            return "\"\"";
        }
    }
}
