package com.agent.gateway.runtime.chat;

import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 跟踪本轮工具调用：累积参数、计算耗时，并产出结构化 {@code tool} SSE。
 * <p>所属模块：runtime。与 {@link AgentScopeEventMapper} 配合，由执行器持有实例。
 */
final class ToolCallTracker {

    private final Map<String, Long> startNanos = new HashMap<>();
    private final Map<String, StringBuilder> argsBuf = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();

    /**
     * 若为工具相关原生事件则映射为网关事件；否则为空，交由通用 mapper。
     *
     * @param event AgentScope 原生事件
     * @return 工具 SSE；非工具事件时 empty
     */
    Optional<AgentEvent> handle(io.agentscope.core.event.AgentEvent event) {
        if (event instanceof ToolCallStartEvent e) {
            String id = safe(e.getToolCallId());
            String name = safe(e.getToolCallName());
            startNanos.put(id, System.nanoTime());
            names.put(id, name);
            argsBuf.put(id, new StringBuilder());
            return Optional.of(AgentScopeEventMapper.toolStart(id, name));
        }
        if (event instanceof ToolCallDeltaEvent e) {
            String id = safe(e.getToolCallId());
            String name = names.getOrDefault(id, safe(e.getToolCallName()));
            names.putIfAbsent(id, name);
            StringBuilder buf = argsBuf.computeIfAbsent(id, k -> new StringBuilder());
            if (e.getDelta() != null) {
                buf.append(e.getDelta());
            }
            return Optional.of(AgentScopeEventMapper.toolArgs(id, name, buf.toString()));
        }
        if (event instanceof ToolCallEndEvent e) {
            // 参数拼装完成，再推一帧完整 args，便于前端定稿摘要
            String id = safe(e.getToolCallId());
            String name = names.getOrDefault(id, safe(e.getToolCallName()));
            String args = argsBuf.getOrDefault(id, new StringBuilder()).toString();
            if (args.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(AgentScopeEventMapper.toolArgs(id, name, args));
        }
        if (event instanceof ToolResultEndEvent e) {
            String id = safe(e.getToolCallId());
            String name = names.getOrDefault(id, safe(e.getToolCallName()));
            Long started = startNanos.remove(id);
            long durationMs = started == null
                    ? 0L
                    : TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            String args = argsBuf.containsKey(id) ? argsBuf.remove(id).toString() : null;
            names.remove(id);
            return Optional.of(AgentScopeEventMapper.toolEnd(
                    id,
                    name,
                    AgentScopeEventMapper.mapToolStatus(e.getState()),
                    durationMs,
                    args));
        }
        return Optional.empty();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
