package com.agent.gateway.tools.mcp;

import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema.Tool;

public record RemoteMcpTool(
        String serverId,
        String serverName,
        Tool definition,
        McpClientWrapper client,
        boolean requireConfirm
) {
}
