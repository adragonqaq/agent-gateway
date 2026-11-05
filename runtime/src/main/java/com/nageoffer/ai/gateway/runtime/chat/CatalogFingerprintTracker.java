package com.nageoffer.ai.gateway.runtime.chat;

import com.nageoffer.ai.gateway.tools.ResolvedCatalog;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight fingerprint tracker used by echo mode and unit tests.
 */
@Component
public class CatalogFingerprintTracker {

    private final ConcurrentHashMap<String, String> fingerprints = new ConcurrentHashMap<>();
    private final AtomicInteger rebuilds = new AtomicInteger();

    public synchronized Snapshot getOrRebuild(String tenantId, ResolvedCatalog catalog) {
        String fp = catalog.fingerprint();
        String prev = fingerprints.put(tenantId, fp);
        if (prev == null || !prev.equals(fp)) {
            rebuilds.incrementAndGet();
        }
        return new Snapshot(tenantId, fp, rebuilds.get());
    }

    public int rebuildCount() {
        return rebuilds.get();
    }

    public record Snapshot(String tenantId, String fingerprint, int rebuildGeneration) {
    }
}
