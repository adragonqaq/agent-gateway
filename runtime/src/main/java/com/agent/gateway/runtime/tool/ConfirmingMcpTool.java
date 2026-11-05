package com.agent.gateway.runtime.tool;

import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.mcp.McpTool;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 带确认策略的 MCP 工具。
 * <p>所属模块：runtime。目录或只读提示要求确认时，调用前先向用户征求许可。
 */
public class ConfirmingMcpTool extends McpTool {

    private final boolean needsConfirm;

    /**
     * @param name 规范化后的工具名
     * @param description 工具说明
     * @param parameters 入参 schema
     * @param outputSchema 出参 schema
     * @param client 已初始化的 MCP 客户端
     * @param mcpName MCP Server 名称
     * @param readOnly 是否只读
     * @param needsConfirm 调用前是否必须确认
     */
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

    /**
     * 按构造时的确认标记决定允许或询问用户。
     *
     * @param toolInput 本次调用入参
     * @param context 权限上下文
     * @return 允许或需要用户确认
     */
    @Override
    public Mono<PermissionDecision> checkPermissions(Map<String, Object> toolInput, PermissionContextState context) {
        if (needsConfirm) {
            // 危险或策略要求确认的调用不能直接打到 MCP
            return Mono.just(PermissionDecision.ask("该操作执行前需要确认"));
        }
        return Mono.just(PermissionDecision.allow("无需确认"));
    }

    /**
     * 工厂方法，供目录组装处保持调用点简短。
     *
     * @param name 工具名
     * @param description 工具说明
     * @param inputSchema 入参 schema
     * @param outputSchema 出参 schema
     * @param client MCP 客户端
     * @param serverName MCP Server 名称
     * @param readOnly 是否只读
     * @param needsConfirm 是否需要确认
     * @return 可注册的确认型 MCP 工具
     */
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
