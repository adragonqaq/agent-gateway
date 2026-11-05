package com.agent.gateway.tools;

import com.agent.gateway.tenant.PolicyEngine;
import com.agent.gateway.tenant.TenantIds;
import com.agent.gateway.tenant.ToolPolicy;
import com.agent.gateway.tools.mcp.RemoteMcpTool;
import com.agent.gateway.tools.mcp.TenantMcpClientPool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 租户工具目录解析器。
 * <p>所属模块：tools。优先通过 MCP 连接池实时发现工具，失败时回退到注册表+占位发现，并套用租户策略。
 */
@Component
public class TenantToolCatalog {

    private final McpRegistry mcpRegistry;
    private final PolicyEngine policyEngine;
    private final ObjectProvider<TenantMcpClientPool> mcpClientPool;
    private final Function<McpServerRecord, List<CatalogTool>> fallbackDiscovery;

    /**
     * 生产环境构造：回退发现为每个 Server 生成 ping 占位工具。
     *
     * @param mcpRegistry MCP 仓库
     * @param policyEngine 租户工具策略引擎
     * @param mcpClientPool MCP 连接池（可选）
     */
    public TenantToolCatalog(
            McpRegistry mcpRegistry,
            PolicyEngine policyEngine,
            ObjectProvider<TenantMcpClientPool> mcpClientPool) {
        this(mcpRegistry, policyEngine, mcpClientPool, server -> List.of(
                new CatalogTool(server.name() + ".ping", "ping " + server.name(), server.id(), true, true)));
    }

    /**
     * 可注入回退发现逻辑的构造，便于单测。
     *
     * @param mcpRegistry MCP 仓库
     * @param policyEngine 策略引擎
     * @param mcpClientPool MCP 连接池
     * @param fallbackDiscovery 无法实时发现时的回退工具生成器
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
     *
     * @param tenantId 租户 ID
     * @return 解析后的目录快照
     */
    public ResolvedCatalog resolve(String tenantId) {
        String id = TenantIds.require(tenantId);
        Map<String, ToolPolicy> policies = policyEngine.policiesFor(id);
        List<CatalogTool> tools = new ArrayList<>();

        TenantMcpClientPool pool = mcpClientPool.getIfAvailable();
        if (pool != null) {
            // 优先实时连接 MCP，以便目录反映远端真实工具列表
            List<RemoteMcpTool> remotes = pool.discover(id, true);
            if (!remotes.isEmpty()) {
                for (RemoteMcpTool remote : remotes) {
                    String name = remote.definition().name();
                    ToolPolicy policy = policies.get(name);
                    if (policy != null && policy.denied()) {
                        // 策略显式拒绝的工具不进入目录，模型无法看到也无法调用
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
                return new ResolvedCatalog(id, List.copyOf(tools));
            }
        }

        // 实时发现为空时回退注册表，避免 MCP 短暂不可用导致目录全空
        for (McpServerRecord server : mcpRegistry.listActive(id)) {
            List<CatalogTool> discovered;
            try {
                discovered = fallbackDiscovery.apply(server);
            } catch (RuntimeException ex) {
                // 发现失败标成不可用，而不是静默丢弃，便于上层感知 MCP 不可达
                tools.add(new CatalogTool(server.name() + ".unavailable", "MCP unreachable", server.id(), false, true));
                continue;
            }
            for (CatalogTool tool : discovered) {
                ToolPolicy policy = policies.get(tool.name());
                if (policy != null && policy.denied()) {
                    // 回退路径同样丢弃被策略拒绝的工具
                    continue;
                }
                boolean confirm = policy == null || policy.requireConfirm();
                tools.add(new CatalogTool(tool.name(), tool.description(), tool.serverId(), tool.available(), confirm));
            }
        }
        return new ResolvedCatalog(id, List.copyOf(tools));
    }
}
