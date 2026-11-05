package com.agent.gateway.tools.mcp;

import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * 已从远端 MCP 发现的工具绑定。
 * <p>所属模块：tools。将工具定义与可复用的 {@link McpClientWrapper} 关联，供 runtime 注册到 Toolkit。
 *
 * @param serverId MCP Server ID
 * @param serverName MCP Server 名称
 * @param definition MCP 工具 schema
 * @param client 已初始化的客户端包装
 * @param requireConfirm 默认是否需要确认
 */
public record RemoteMcpTool(
        String serverId,
        String serverName,
        Tool definition,
        McpClientWrapper client,
        boolean requireConfirm
) {
}
