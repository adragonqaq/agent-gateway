package com.agent.gateway.runtime.tool;

import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.mcp.McpTool;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * MCP tool that asks for confirmation when the catalog marks requireConfirm=true.
 */
public class ConfirmingMcpTool extends McpTool {

    private final boolean needsConfirm;

    public ConfirmingMcpTool(
            String name,
            String description,
            Map<String, Object> parameters,
            Map<String, Object> outputSchema,
            McpClientWrapper client,
            String mcpName,
            boolean readOnly,
            boolean needsConfirm) {
        super(name, description, parameters, outputSchema, client, null, mcpName, readOnly);
        this.needsConfirm = needsConfirm;
    }

    @Override
    public Mono<PermissionDecision> checkPermissions(Map<String, Object> toolInput, PermissionContextState context) {
        if (needsConfirm) {
            return Mono.just(PermissionDecision.ask("该操作执行前需要确认"));
        }
        return Mono.just(PermissionDecision.allow("无需确认"));
    }

    public static ConfirmingMcpTool from(
            String name,
            String description,
            Map<String, Object> inputSchema,
            Map<String, Object> outputSchema,
            McpClientWrapper client,
            String serverName,
            boolean readOnly,
            boolean needsConfirm) {
        return new ConfirmingMcpTool(
                name, description, inputSchema, outputSchema, client, serverName, readOnly, needsConfirm);
    }
}
