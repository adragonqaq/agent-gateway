package com.agent.gateway.runtime.chat;

import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolResultState;
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
        assertTrue(tool.data().contains("\"phase\":\"start\""));
        assertTrue(tool.data().contains("\"name\":\"weather_ping\""));
        assertTrue(tool.data().contains("\"toolCallId\":\"c1\""));
    }

    @Test
    void mapsToolArgsAndEndJson() {
        AgentEvent args = AgentScopeEventMapper.map(
                new ToolCallDeltaEvent("r", "c1", "weather_ping", "{\"city\":\"")).orElseThrow();
        assertEquals("tool", args.type());
        assertTrue(args.data().contains("\"phase\":\"args\""));
        assertTrue(args.data().contains("argsJson"));

        AgentEvent end = AgentScopeEventMapper.map(
                new ToolResultEndEvent("r", "c1", "weather_ping", ToolResultState.SUCCESS)).orElseThrow();
        assertTrue(end.data().contains("\"phase\":\"end\""));
        assertTrue(end.data().contains("\"status\":\"ok\""));
    }

    @Test
    void toolTrackerAccumulatesArgsAndDuration() {
        ToolCallTracker tracker = new ToolCallTracker();
        AgentEvent start = tracker.handle(new ToolCallStartEvent("r", "c1", "demo_cli")).orElseThrow();
        assertTrue(start.data().contains("\"phase\":\"start\""));

        tracker.handle(new ToolCallDeltaEvent("r", "c1", "demo_cli", "{\"command\":\""));
        AgentEvent args = tracker.handle(new ToolCallDeltaEvent("r", "c1", "demo_cli", "ls\"}")).orElseThrow();
        assertTrue(args.data().contains("\"phase\":\"args\""));
        assertTrue(args.data().contains("command"));
        assertTrue(args.data().contains("ls"));

        AgentEvent end = tracker.handle(
                new ToolResultEndEvent("r", "c1", "demo_cli", ToolResultState.SUCCESS)).orElseThrow();
        assertTrue(end.data().contains("\"phase\":\"end\""));
        assertTrue(end.data().contains("\"durationMs\""));
        assertTrue(end.data().contains("\"status\":\"ok\""));
        assertTrue(end.data().contains("ls"));
    }

    @Test
    void donePayloadIncludesElapsed() {
        AgentEvent done = AgentScopeEventMapper.done("ok", 42);
        assertEquals("done", done.type());
        assertTrue(done.data().contains("\"status\":\"ok\""));
        assertTrue(done.data().contains("\"elapsedMs\":42"));
    }

    @Test
    void donePayloadIncludesTokenUsageWhenPresent() {
        AgentEvent done = AgentScopeEventMapper.done("ok", 42, 100, 20);
        assertTrue(done.data().contains("\"promptTokens\":100"));
        assertTrue(done.data().contains("\"completionTokens\":20"));
        assertTrue(done.data().contains("\"totalTokens\":120"));
    }

    @Test
    void donePayloadOmitsTokensWhenUnknown() {
        AgentEvent done = AgentScopeEventMapper.done("ok", 42, null, null);
        assertTrue(done.data().contains("\"elapsedMs\":42"));
        assertTrue(!done.data().contains("promptTokens"));
        assertTrue(!done.data().contains("completionTokens"));
        assertTrue(!done.data().contains("totalTokens"));
    }

    @Test
    void skipsFinalResultWhenTextDeltasAlreadyStreamed() {
        Msg result = Msg.builder().role(MsgRole.ASSISTANT).textContent("hello full").build();
        AgentResultEvent finalEvent = new AgentResultEvent(result);

        assertEquals("hello full",
                AgentScopeEventMapper.map(finalEvent, false).orElseThrow().data());
        assertTrue(AgentScopeEventMapper.map(finalEvent, true).isEmpty());
    }
}
