package com.nageoffer.ai.gateway.runtime.chat;

import com.nageoffer.ai.gateway.tools.ResolvedCatalog;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks catalog fingerprints so AgentScope wiring can rebuild when tools change.
 * Full AgentScope ReAct loop is behind gateway.agent.mode=agentscope (future).
 */
@Component
public class ReActAgentProvider {

    private final ConcurrentHashMap<String, String> fingerprints = new ConcurrentHashMap<>();
    private final AtomicInteger rebuilds = new AtomicInteger();

    public synchronized ActiveSnapshot getOrRebuild(String tenantId, ResolvedCatalog catalog) {
        String fp = catalog.fingerprint();
        String prev = fingerprints.put(tenantId, fp);
        if (prev == null || !prev.equals(fp)) {
            rebuilds.incrementAndGet();
        }
        return new ActiveSnapshot(tenantId, fp, rebuilds.get());
    }

    public int rebuildCount() {
        return rebuilds.get();
    }

    public record ActiveSnapshot(String tenantId, String fingerprint, int rebuildGeneration) {
    }
}
