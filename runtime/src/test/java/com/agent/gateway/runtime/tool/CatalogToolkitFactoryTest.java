package com.agent.gateway.runtime.tool;

import com.agent.gateway.tools.CatalogTool;
import com.agent.gateway.tools.ResolvedCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogToolkitFactoryTest {

    @Test
    void unavailableToolsRegisteredAsDegradedErrorStubs() {
        ResolvedCatalog catalog = new ResolvedCatalog("tA", List.of(
                new CatalogTool("orders.unavailable", "MCP unreachable", "s2", false, true),
                new CatalogTool("weather.ping", "ping", "s1", true, false)));
        var toolkit = new CatalogToolkitFactory().build(catalog);
        assertTrue(toolkit.getToolNames().contains("orders_unavailable"));
        assertTrue(toolkit.getToolNames().contains("weather_ping"));
    }
}
