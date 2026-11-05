package com.agent.gateway.tools;

import com.agent.gateway.tenant.PolicyEngine;
import com.agent.gateway.tenant.TenantIds;
import com.agent.gateway.tenant.ToolPolicy;
import com.agent.gateway.tools.mcp.RemoteMcpTool;
import com.agent.gateway.tools.mcp.TenantMcpClientPool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 租户工具目录解析器。
 * <p>所属模块：tools。优先通过 MCP 连接池实时发现工具，失败时回退到注册表+占位发现，并套用租户策略。
 * 部分 Server 不可达时保留健康工具，并为宕机 Server 写入 unavailable 标记。
 */
@Component
public class TenantToolCatalog {

    private final McpRegistry mcpRegistry;
    private final PolicyEngine policyEngine;
    private final ObjectProvider<TenantMcpClientPool> mcpClientPool;
    private final Function<McpServerRecord, List<CatalogTool>> fallbackDiscovery;

    /**
     * 生产环境构造：回退发现为每个 Server 生成 ping 占位工具。
     */
    @Autowired
    public TenantToolCatalog(
            McpRegistry mcpRegistry,
            PolicyEngine policyEngine,
            ObjectProvider<TenantMcpClientPool> mcpClientPool) {
        this(mcpRegistry, policyEngine, mcpClientPool, server -> List.of(
                new CatalogTool(server.name() + ".ping", "ping " + server.name(), server.id(), true, true)));
    }

    /**
     * 可注入回退发现逻辑的构造，便于单测。
     */
    TenantToolCatalog(
            McpRegistry mcpRegistry,
            PolicyEngine policyEngine,
            ObjectProvider<TenantMcpClientPool> mcpClientPool,
            Function<McpServerRecord, List<CatalogTool>> fallbackDiscovery) {
        this.mcpRegistry = mcpRegistry;
        this.policyEngine = policyEngine;
        this.mcpClientPool = mcpClientPool;
        this.fallbackDiscovery = fallbackDiscovery;
    }

    /**
     * 解析指定租户当前可见工具目录。
     */
    public ResolvedCatalog resolve(String tenantId) {
        String id = TenantIds.require(tenantId);
        Map<String, ToolPolicy> policies = policyEngine.policiesFor(id);
        List<CatalogTool> tools = new ArrayList<>();
        List<McpServerRecord> servers = mcpRegistry.listActive(id);

        TenantMcpClientPool pool = mcpClientPool.getIfAvailable();
        if (pool != null) {
            List<RemoteMcpTool> remotes = pool.discover(id, true);
            Set<String> healthyServerIds = new HashSet<>();
            for (RemoteMcpTool remote : remotes) {
                healthyServerIds.add(remote.serverId());
                String name = remote.definition().name();
                ToolPolicy policy = policies.get(name);
                if (policy != null && policy.denied()) {
                    continue;
                }
                boolean confirm = policy == null ? remote.requireConfirm() : policy.requireConfirm();
                tools.add(new CatalogTool(
                        name,
                        remote.definition().description() == null ? name : remote.definition().description(),
                        remote.serverId(),
                        true,
                        confirm));
            }
            // 注册表中有、但本轮 discover 未产出任何工具的 Server → 标不可用
            for (McpServerRecord server : servers) {
                if (!healthyServerIds.contains(server.id())) {
                    tools.add(new CatalogTool(
                            server.name() + ".unavailable",
                            "MCP unreachable: " + server.name(),
                            server.id(),
                            false,
                            true));
                }
            }
            if (!tools.isEmpty() || servers.isEmpty()) {
                return new ResolvedCatalog(id, List.copyOf(tools));
            }
        }

        // 无连接池或实时发现彻底为空：回退注册表占位发现
        for (McpServerRecord server : servers) {
            List<CatalogTool> discovered;
            try {
                discovered = fallbackDiscovery.apply(server);
            } catch (RuntimeException ex) {
                tools.add(new CatalogTool(server.name() + ".unavailable", "MCP unreachable", server.id(), false, true));
                continue;
            }
            for (CatalogTool tool : discovered) {
                ToolPolicy policy = policies.get(tool.name());
                if (policy != null && policy.denied()) {
                    continue;
                }
                boolean confirm = policy == null || policy.requireConfirm();
                tools.add(new CatalogTool(tool.name(), tool.description(), tool.serverId(), tool.available(), confirm));
            }
        }
        return new ResolvedCatalog(id, List.copyOf(tools));
    }
}
