package com.agent.gateway.runtime.chat;

import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 将 AgentScope 原生事件映射为网关 {@link AgentEvent}。
 * <p>所属模块：runtime。无法识别的事件丢弃，避免把内部事件泄漏给调用方。
 */
final class AgentScopeEventMapper {

    /**
     * 工具类，禁止实例化。
     */
    private AgentScopeEventMapper() {
    }

    /**
     * 映射单条 AgentScope 事件。
     *
     * @param event 原生事件
     * @return 可对外暴露的事件；无法识别时为空
     */
    static Optional<AgentEvent> map(io.agentscope.core.event.AgentEvent event) {
        if (event instanceof ThinkingBlockDeltaEvent e) {
            // 思考增量单独成事件，便于前端展示推理过程
            return Optional.of(new AgentEvent("thinking", nullToEmpty(e.getDelta())));
        }
        if (event instanceof TextBlockDeltaEvent e) {
            // 文本增量作为回答流，调用方按序拼接
            return Optional.of(new AgentEvent("answer", nullToEmpty(e.getDelta())));
        }
        if (event instanceof ToolCallStartEvent e) {
            // 只暴露工具名与调用 ID，参数留在执行器内部
            return Optional.of(new AgentEvent("tool", e.getToolCallName() + "#" + e.getToolCallId()));
        }
        if (event instanceof RequireUserConfirmEvent e) {
            // 确认事件只汇总工具名，具体参数由确认闸门另行持有
            String tools = e.getToolCalls() == null ? "" : e.getToolCalls().stream()
                    .map(t -> t.getName())
                    .collect(Collectors.joining(","));
            return Optional.of(new AgentEvent("confirm", tools));
        }
        if (event instanceof AgentResultEvent e) {
            // 最终结果再补一条完整回答，避免调用方只收到增量时漏收全文
            return Optional.of(new AgentEvent("answer", extractText(e.getResult())));
        }
        return Optional.empty();
    }

    /**
     * 从消息内容中拼接全部文本块。
     *
     * @param msg Agent 最终消息；可为 null
     * @return 拼接后的文本，无内容时为空串
     */
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

    /**
     * 空增量按空串处理，避免事件 data 为 null。
     *
     * @param s 原始增量
     * @return 非 null 字符串
     */
    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
