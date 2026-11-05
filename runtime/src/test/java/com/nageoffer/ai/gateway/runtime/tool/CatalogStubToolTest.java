package com.nageoffer.ai.gateway.runtime.tool;

import com.nageoffer.ai.gateway.tools.CatalogTool;
import com.nageoffer.ai.gateway.tools.ResolvedCatalog;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogStubToolTest {

    @Test
    void buildsToolkitOnlyForAvailableTools() {
        CatalogToolkitFactory factory = new CatalogToolkitFactory();
        ResolvedCatalog catalog = new ResolvedCatalog("t1", List.of(
                new CatalogTool("weather.ping", "ping", "s1", true, false),
                new CatalogTool("tickets.book", "book", "s2", false, true)));
        Toolkit toolkit = factory.build(catalog);
        assertEquals(1, toolkit.getToolNames().size());
        assertTrue(toolkit.getToolNames().contains("weather_ping"));
    }
}
