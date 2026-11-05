package com.nageoffer.ai.gateway.runtime.chat;

import com.nageoffer.ai.gateway.runtime.config.GatewayAgentProperties;
import com.nageoffer.ai.gateway.runtime.tool.CatalogToolkitFactory;
import com.nageoffer.ai.gateway.tools.ResolvedCatalog;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds/caches tenant-scoped ReActAgent instances keyed by catalog fingerprint.
 */
@Component
@ConditionalOnProperty(name = "gateway.agent.mode", havingValue = "agentscope")
public class ReActAgentProvider {

    private final GatewayAgentProperties properties;
    private final OpenAIChatModel chatModel;
    private final InMemoryAgentStateStore stateStore;
    private final CatalogToolkitFactory toolkitFactory;
    private final ConcurrentHashMap<String, CachedAgent> cache = new ConcurrentHashMap<>();
    private final AtomicInteger rebuilds = new AtomicInteger();

    public ReActAgentProvider(
            GatewayAgentProperties properties,
            OpenAIChatModel chatModel,
            InMemoryAgentStateStore stateStore,
            CatalogToolkitFactory toolkitFactory) {
        this.properties = properties;
        this.chatModel = chatModel;
        this.stateStore = stateStore;
        this.toolkitFactory = toolkitFactory;
    }

    public ActiveSnapshot getOrRebuild(String tenantId, ResolvedCatalog catalog) {
        String fp = catalog.fingerprint();
        CachedAgent current = cache.get(tenantId);
        if (current != null && current.fingerprint().equals(fp)) {
            return new ActiveSnapshot(tenantId, fp, current.agent(), rebuilds.get());
        }
        synchronized (this) {
            current = cache.get(tenantId);
            if (current != null && current.fingerprint().equals(fp)) {
                return new ActiveSnapshot(tenantId, fp, current.agent(), rebuilds.get());
            }
            ReActAgent agent = ReActAgent.builder()
                    .name("AgentGateway")
                    .sysPrompt(properties.getPersona())
                    .model(chatModel)
                    .toolkit(toolkitFactory.build(catalog))
                    .maxIters(properties.getMaxIters())
                    .maxRetries(properties.getMaxRetries())
                    .enablePendingToolRecovery(true)
                    .stateStore(stateStore)
                    .build();
            rebuilds.incrementAndGet();
            cache.put(tenantId, new CachedAgent(fp, agent));
            return new ActiveSnapshot(tenantId, fp, agent, rebuilds.get());
        }
    }

    public int rebuildCount() {
        return rebuilds.get();
    }

    public record ActiveSnapshot(String tenantId, String fingerprint, ReActAgent agent, int rebuildGeneration) {
    }

    private record CachedAgent(String fingerprint, ReActAgent agent) {
    }
}
