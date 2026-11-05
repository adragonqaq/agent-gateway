package com.agent.gateway.mcp.config;

import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;

/**
 * MCP 工具读写注解常量。gateway 侧按 {@code readOnlyHint} 决定是否走 ConfirmGate。
 */
public final class McpToolAnnotations {

    /** 只读：不改数据，默认无需确认（除非策略强制） */
    public static final ToolAnnotations READ_ONLY = new ToolAnnotations(null, true, false, true, false, null);

    /** 写操作：有副作用，gateway 会要求人工确认 */
    public static final ToolAnnotations WRITE = new ToolAnnotations(null, false, false, false, false, null);

    private McpToolAnnotations() {
    }
}
