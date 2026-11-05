package com.agent.gateway.runtime.chat;

import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tools.ResolvedCatalog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "gateway.agent.mode", havingValue = "echo", matchIfMissing = true)
public class EchoAgentExecutor implements AgentExecutor {

    private final CatalogFingerprintTracker fingerprintTracker;

    public EchoAgentExecutor(CatalogFingerprintTracker fingerprintTracker) {
        this.fingerprintTracker = fingerprintTracker;
    }

    @Override
    public void run(TenantContext tenant, String conversationId, String message,
                    ResolvedCatalog catalog, String priorState,
                    Consumer<AgentEvent> events, Consumer<String> stateSaver) {
        fingerprintTracker.getOrRebuild(tenant.tenantId(), catalog);
        events.accept(new AgentEvent("thinking", "planning with " + catalog.tools().size() + " tools"));
        String toolNames = catalog.tools().stream().map(CatalogToolName::of).collect(Collectors.joining(","));
        if (!toolNames.isBlank()) {
            events.accept(new AgentEvent("tool", toolNames));
        }
        events.accept(new AgentEvent("answer", "echo: " + message));
        events.accept(new AgentEvent("done", "ok"));
        stateSaver.accept("{\"last\":\"" + message.replace("\"", "'") + "\"}");
    }

    private static final class CatalogToolName {
        static String of(com.agent.gateway.tools.CatalogTool tool) {
            return tool.name();
        }
    }
}
