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

final class AgentScopeEventMapper {

    private AgentScopeEventMapper() {
    }

    static Optional<AgentEvent> map(io.agentscope.core.event.AgentEvent event) {
        if (event instanceof ThinkingBlockDeltaEvent e) {
            return Optional.of(new AgentEvent("thinking", nullToEmpty(e.getDelta())));
        }
        if (event instanceof TextBlockDeltaEvent e) {
            return Optional.of(new AgentEvent("answer", nullToEmpty(e.getDelta())));
        }
        if (event instanceof ToolCallStartEvent e) {
            return Optional.of(new AgentEvent("tool", e.getToolCallName() + "#" + e.getToolCallId()));
        }
        if (event instanceof RequireUserConfirmEvent e) {
            String tools = e.getToolCalls() == null ? "" : e.getToolCalls().stream()
                    .map(t -> t.getName())
                    .collect(Collectors.joining(","));
            return Optional.of(new AgentEvent("confirm", tools));
        }
        if (event instanceof AgentResultEvent e) {
            return Optional.of(new AgentEvent("answer", extractText(e.getResult())));
        }
        return Optional.empty();
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
