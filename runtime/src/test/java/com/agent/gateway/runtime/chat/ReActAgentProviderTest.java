package com.agent.gateway.runtime.chat;

import com.agent.gateway.tools.CatalogTool;
import com.agent.gateway.tools.ResolvedCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReActAgentProviderTest {

    @Test
    void rebuildsWhenFingerprintChanges() {
        CatalogFingerprintTracker tracker = new CatalogFingerprintTracker();
        ResolvedCatalog a = new ResolvedCatalog("t1", List.of(
                new CatalogTool("a.ping", "d", "s1", true, true)));
        ResolvedCatalog b = new ResolvedCatalog("t1", List.of(
                new CatalogTool("b.ping", "d", "s1", true, true)));
        tracker.getOrRebuild("t1", a);
        tracker.getOrRebuild("t1", a);
        assertEquals(1, tracker.rebuildCount());
        tracker.getOrRebuild("t1", b);
        assertEquals(2, tracker.rebuildCount());
    }
}
