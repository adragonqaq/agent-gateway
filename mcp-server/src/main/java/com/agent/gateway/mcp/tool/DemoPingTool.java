package com.agent.gateway.mcp.tool;

import com.agent.gateway.mcp.config.McpToolAnnotations;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.agent.gateway.mcp.tool.McpToolSchema.string;

/** 连通性探测：只读 echo。 */
@Component
public class DemoPingTool {

    public static final String TOOL_ID = "demo_ping";

    @Bean
    public McpServerFeatures.SyncToolSpecification demoPingToolSpecification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(buildTool())
                .callHandler((exchange, request) -> handle(request))
                .build();
    }

    private Tool buildTool() {
        JsonSchema schema = McpToolSchema.object()
                .optional(string("message", "可选回显文本；不填返回 pong").defaultTo("pong"))
                .build();
        return Tool.builder()
                .name(TOOL_ID)
                .description("探测 demo MCP Server 是否可达；返回 pong 或回显 message")
                .inputSchema(schema)
                .annotations(McpToolAnnotations.READ_ONLY)
                .build();
    }

    CallToolResult handle(CallToolRequest request) {
        Map<String, Object> args = McpToolResults.args(request);
        Object raw = args.get("message");
        String message = raw == null || String.valueOf(raw).isBlank() ? "pong" : String.valueOf(raw).trim();
        return McpToolResults.success("demo_ping: " + message);
    }
}
