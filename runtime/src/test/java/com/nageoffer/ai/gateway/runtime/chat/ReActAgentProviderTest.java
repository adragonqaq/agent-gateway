package com.nageoffer.ai.gateway.runtime.chat;

import com.nageoffer.ai.gateway.tools.CatalogTool;
import com.nageoffer.ai.gateway.tools.ResolvedCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReActAgentProviderTest {

    @Test
    void rebuildsWhenFingerprintChanges() {
        ReActAgentProvider provider = new ReActAgentProvider();
        ResolvedCatalog a = new ResolvedCatalog("t1", List.of(
                new CatalogTool("a.ping", "d", "s1", true, true)));
        ResolvedCatalog b = new ResolvedCatalog("t1", List.of(
                new CatalogTool("b.ping", "d", "s1", true, true)));
        provider.getOrRebuild("t1", a);
        provider.getOrRebuild("t1", a);
        assertEquals(1, provider.rebuildCount());
        provider.getOrRebuild("t1", b);
        assertEquals(2, provider.rebuildCount());
    }
}
