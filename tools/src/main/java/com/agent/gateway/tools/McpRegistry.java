package com.agent.gateway.tools;

import java.util.List;
import java.util.Optional;

/**
 * MCP Server 持久化仓库接口。
 * <p>所属模块：tools。按租户隔离地登记、查询 MCP Server 配置。
 */
public interface McpRegistry {

    /**
     * 按 id + tenantId 幂等写入或更新 MCP Server。
     *
     * @param server 待持久化记录
     */
    void upsert(McpServerRecord server);

    /**
     * 列出租户状态为 ACTIVE 的 MCP Server。
     *
     * @param tenantId 租户 ID
     * @return 活跃 Server 列表
     */
    List<McpServerRecord> listActive(String tenantId);

    /**
     * 按租户与 Server ID 查找单条记录。
     *
     * @param tenantId 租户 ID
     * @param serverId Server ID
     * @return 存在则返回记录
     */
    Optional<McpServerRecord> find(String tenantId, String serverId);
}
