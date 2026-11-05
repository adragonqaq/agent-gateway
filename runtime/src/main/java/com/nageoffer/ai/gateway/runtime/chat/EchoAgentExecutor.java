package com.nageoffer.ai.gateway.runtime.chat;

import com.nageoffer.ai.gateway.tenant.TenantContext;
import com.nageoffer.ai.gateway.tools.ResolvedCatalog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(name = "gateway.agent.mode", havingValue = "echo", matchIfMissing = true)
public class EchoAgentExecutor implements AgentExecutor {

    @Override
    public void run(TenantContext tenant, String conversationId, String message,
                    ResolvedCatalog catalog, String priorState,
                    Consumer<AgentEvent> events, Consumer<String> stateSaver) {
        events.accept(new AgentEvent("thinking", "planning with " + catalog.tools().size() + " tools"));
        String toolNames = catalog.tools().stream().map(t -> t.name()).collect(Collectors.joining(","));
        if (!toolNames.isBlank()) {
            events.accept(new AgentEvent("tool", toolNames));
        }
        events.accept(new AgentEvent("answer", "echo: " + message));
        events.accept(new AgentEvent("done", "ok"));
        stateSaver.accept("{\"last\":\"" + message.replace("\"", "'") + "\"}");
    }
}
