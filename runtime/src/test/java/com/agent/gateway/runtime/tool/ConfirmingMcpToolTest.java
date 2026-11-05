package com.agent.gateway.runtime.tool;

import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ConfirmingMcpToolTest {

    @Test
    void asksWhenNeedsConfirm() {
        ConfirmingMcpTool tool = ConfirmingMcpTool.from(
                "delete_order", "delete", Map.of("type", "object"), null,
                mock(McpClientWrapper.class), "orders", false, true);
        PermissionDecision decision = tool.checkPermissions(Map.of(), null).block();
        assertEquals(PermissionBehavior.ASK, decision.getBehavior());
    }

    @Test
    void allowsWhenNoConfirm() {
        ConfirmingMcpTool tool = ConfirmingMcpTool.from(
                "ping", "ping", Map.of("type", "object"), null,
                mock(McpClientWrapper.class), "weather", true, false);
        PermissionDecision decision = tool.checkPermissions(Map.of(), null).block();
        assertEquals(PermissionBehavior.ALLOW, decision.getBehavior());
    }
}
