package com.nageoffer.ai.gateway.tools;

import com.nageoffer.ai.gateway.tenant.PolicyEngine;
import com.nageoffer.ai.gateway.tenant.TenantIds;
import com.nageoffer.ai.gateway.tenant.ToolPolicy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class TenantToolCatalog {

    private final McpRegistry mcpRegistry;
    private final PolicyEngine policyEngine;
    private final Function<McpServerRecord, List<CatalogTool>> discovery;

    public TenantToolCatalog(McpRegistry mcpRegistry, PolicyEngine policyEngine) {
        this(mcpRegistry, policyEngine, server -> List.of(
                new CatalogTool(server.name() + ".ping", "ping " + server.name(), server.id(), true, true)));
    }

    TenantToolCatalog(McpRegistry mcpRegistry, PolicyEngine policyEngine,
                      Function<McpServerRecord, List<CatalogTool>> discovery) {
        this.mcpRegistry = mcpRegistry;
        this.policyEngine = policyEngine;
        this.discovery = discovery;
    }

    public ResolvedCatalog resolve(String tenantId) {
        String id = TenantIds.require(tenantId);
        Map<String, ToolPolicy> policies = policyEngine.policiesFor(id);
        List<CatalogTool> tools = new ArrayList<>();
        for (McpServerRecord server : mcpRegistry.listActive(id)) {
            List<CatalogTool> discovered;
            try {
                discovered = discovery.apply(server);
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
