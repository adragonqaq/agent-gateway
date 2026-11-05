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

@Component
public class TenantToolCatalog {

    private final McpRegistry mcpRegistry;
    private final PolicyEngine policyEngine;
    private final ObjectProvider<TenantMcpClientPool> mcpClientPool;
    private final Function<McpServerRecord, List<CatalogTool>> fallbackDiscovery;

    public TenantToolCatalog(
            McpRegistry mcpRegistry,
            PolicyEngine policyEngine,
            ObjectProvider<TenantMcpClientPool> mcpClientPool) {
        this(mcpRegistry, policyEngine, mcpClientPool, server -> List.of(
                new CatalogTool(server.name() + ".ping", "ping " + server.name(), server.id(), true, true)));
    }

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

    public ResolvedCatalog resolve(String tenantId) {
        String id = TenantIds.require(tenantId);
        Map<String, ToolPolicy> policies = policyEngine.policiesFor(id);
        List<CatalogTool> tools = new ArrayList<>();

        TenantMcpClientPool pool = mcpClientPool.getIfAvailable();
        if (pool != null) {
            List<RemoteMcpTool> remotes = pool.discover(id, true);
            if (!remotes.isEmpty()) {
                for (RemoteMcpTool remote : remotes) {
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
                return new ResolvedCatalog(id, List.copyOf(tools));
            }
        }

        for (McpServerRecord server : mcpRegistry.listActive(id)) {
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
