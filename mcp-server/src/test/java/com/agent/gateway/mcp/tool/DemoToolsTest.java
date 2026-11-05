package com.agent.gateway.mcp.tool;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoToolsTest {

    @Test
    void pingDefaultsToPong() {
        CallToolResult result = new DemoPingTool().handle(request(Map.of()));
        assertFalse(Boolean.TRUE.equals(result.isError()));
        assertTrue(text(result).contains("pong"));
    }

    @Test
    void weatherRequiresCity() {
        CallToolResult missing = new DemoWeatherTool().handle(request(Map.of()));
        assertTrue(Boolean.TRUE.equals(missing.isError()));

        CallToolResult ok = new DemoWeatherTool().handle(request(Map.of("city", "上海")));
        assertFalse(Boolean.TRUE.equals(ok.isError()));
        assertTrue(text(ok).contains("上海"));
    }

    @Test
    void ticketSubmitCreatesId() {
        CallToolResult result = new DemoTicketSubmitTool()
                .handle(request(Map.of("title", "打印机坏了", "detail", "3 楼")));
        assertFalse(Boolean.TRUE.equals(result.isError()));
        assertTrue(text(result).contains("TKT-"));
        assertTrue(text(result).contains("打印机坏了"));
    }

    private static CallToolRequest request(Map<String, Object> args) {
        return CallToolRequest.builder().name("t").arguments(args).build();
    }

    private static String text(CallToolResult result) {
        return result.content().stream()
                .filter(TextContent.class::isInstance)
                .map(c -> ((TextContent) c).text())
                .findFirst()
                .orElse("");
    }
}
