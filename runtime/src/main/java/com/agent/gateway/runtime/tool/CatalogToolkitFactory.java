package com.agent.gateway.runtime.tool;

import com.agent.gateway.tools.CatalogTool;
import com.agent.gateway.tools.ResolvedCatalog;
import com.agent.gateway.tools.mcp.RemoteMcpTool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpTool;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CatalogToolkitFactory {

    public Toolkit build(ResolvedCatalog catalog) {
        return build(catalog, List.of());
    }

    public Toolkit build(ResolvedCatalog catalog, List<RemoteMcpTool> remotes) {
        Toolkit toolkit = new Toolkit();
        Map<String, RemoteMcpTool> byName = new HashMap<>();
        for (RemoteMcpTool remote : remotes) {
            byName.put(sanitize(remote.definition().name()), remote);
        }

        Set<String> registered = toolkit.getToolNames();
        for (RemoteMcpTool remote : remotes) {
            McpSchema.Tool def = remote.definition();
            String name = sanitize(def.name());
            if (registered.contains(name)) {
                continue;
            }
            Boolean readOnlyHint = def.annotations() == null ? null : def.annotations().readOnlyHint();
            boolean readOnly = Boolean.TRUE.equals(readOnlyHint);
            boolean needsConfirm = remote.requireConfirm() || !readOnly;
            Map<String, Object> params = McpTool.convertMcpSchemaToParameters(def.inputSchema(), Set.of());
            toolkit.registerAgentTool(ConfirmingMcpTool.from(
                    name,
                    def.description() == null ? name : def.description(),
                    params,
                    def.outputSchema(),
                    remote.client(),
                    remote.serverName(),
                    readOnly,
                    needsConfirm));
        }

        for (CatalogTool tool : catalog.tools()) {
            if (!tool.available()) {
                continue;
            }
            String name = CatalogStubTool.sanitize(tool.name());
            if (toolkit.getToolNames().contains(name) || byName.containsKey(name)) {
                continue;
            }
            toolkit.registerAgentTool(new CatalogStubTool(tool));
        }
        return toolkit;
    }

    private static String sanitize(String raw) {
        return CatalogStubTool.sanitize(raw);
    }
}
