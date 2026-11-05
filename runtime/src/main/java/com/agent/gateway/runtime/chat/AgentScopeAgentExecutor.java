package com.agent.gateway.runtime.chat;

import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tools.ResolvedCatalog;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "gateway.agent.mode", havingValue = "agentscope")
public class AgentScopeAgentExecutor implements AgentExecutor {

    private final ReActAgentProvider agentProvider;

    public AgentScopeAgentExecutor(ReActAgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    @Override
    public void run(TenantContext tenant, String conversationId, String message,
                    ResolvedCatalog catalog, String priorState,
                    Consumer<AgentEvent> events, Consumer<String> stateSaver) {
        ReActAgentProvider.ActiveSnapshot snapshot = agentProvider.getOrRebuild(tenant.tenantId(), catalog);
        ReActAgent agent = snapshot.agent();
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .userId(tenant.tenantId())
                .sessionId(conversationId)
                .build();

        StringJoiner answer = new StringJoiner("");
        AtomicReference<Throwable> error = new AtomicReference<>();
        try {
            agent.streamEvents(message, runtimeContext)
                    .doOnNext(scopeEvent -> AgentScopeEventMapper.map(scopeEvent).ifPresent(mapped -> {
                        events.accept(mapped);
                        if ("answer".equals(mapped.type())) {
                            answer.add(mapped.data());
                        }
                    }))
                    .doOnError(error::set)
                    .blockLast(Duration.ofMinutes(5));
        } catch (RuntimeException ex) {
            error.set(ex);
        }

        if (error.get() != null) {
            events.accept(new AgentEvent("error", error.get().getMessage()));
            events.accept(new AgentEvent("done", "error"));
            throw new IllegalStateException("AgentScope run failed", error.get());
        }

        events.accept(new AgentEvent("done", "ok"));
        String payload = "{\"last\":\"" + escape(answer.toString()) + "\",\"prior\":"
                + (priorState == null ? "\"\"" : priorState) + "}";
        stateSaver.accept(payload);
        try {
            agent.saveAgentState(tenant.tenantId(), conversationId);
        } catch (Exception ignored) {
            // SessionStateStore already received a compact snapshot above.
        }
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "'").replace("\n", "\\n");
    }
}
