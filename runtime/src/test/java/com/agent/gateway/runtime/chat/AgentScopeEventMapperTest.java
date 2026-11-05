package com.agent.gateway.runtime.chat;

import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentScopeEventMapperTest {

    @Test
    void mapsCoreEventTypes() {
        assertEquals("thinking",
                AgentScopeEventMapper.map(new ThinkingBlockDeltaEvent("r", "b", "hmm")).orElseThrow().type());
        assertEquals("answer",
                AgentScopeEventMapper.map(new TextBlockDeltaEvent("r", "b", "hi")).orElseThrow().type());
        AgentEvent tool = AgentScopeEventMapper.map(new ToolCallStartEvent("r", "c1", "weather_ping")).orElseThrow();
        assertEquals("tool", tool.type());
        assertTrue(tool.data().startsWith("weather_ping#"));
    }
}
