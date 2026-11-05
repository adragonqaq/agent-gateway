package com.agent.gateway.tools;

/**
 * 工具目录中的单个工具条目。
 * <p>所属模块：tools。描述租户可见工具的名称、来源 MCP Server、可用性与是否需确认。
 *
 * @param name 工具名称
 * @param description 工具说明
 * @param serverId 所属 MCP Server ID
 * @param available 是否当前可用
 * @param requireConfirm 调用前是否需要人工确认
 */
public record CatalogTool(
        String name,
        String description,
        String serverId,
        boolean available,
        boolean requireConfirm
) {
}
