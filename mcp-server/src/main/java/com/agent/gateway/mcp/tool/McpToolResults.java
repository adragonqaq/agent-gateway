package com.agent.gateway.mcp.tool;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import java.util.List;
import java.util.Map;

/** 工具调用结果与参数读取辅助。 */
public final class McpToolResults {

    private McpToolResults() {
    }

    public static Map<String, Object> args(CallToolRequest request) {
        return request != null && request.arguments() != null ? request.arguments() : Map.of();
    }

    public static CallToolResult success(String text) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(text)))
                .isError(false)
                .build();
    }

    public static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(message)))
                .isError(true)
                .build();
    }
}
