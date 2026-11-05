package com.agent.gateway.runtime.chat;

import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tools.ResolvedCatalog;

import java.util.List;
import java.util.function.Consumer;

public interface AgentExecutor {

    void run(TenantContext tenant, String conversationId, String message,
             ResolvedCatalog catalog, String priorState,
             Consumer<AgentEvent> events, Consumer<String> stateSaver);
}
