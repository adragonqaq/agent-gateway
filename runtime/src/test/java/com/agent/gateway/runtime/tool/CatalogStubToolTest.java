package com.agent.gateway.runtime.tool;

import com.agent.gateway.tools.CatalogTool;
import com.agent.gateway.tools.ResolvedCatalog;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogStubToolTest {

    @Test
    void buildsToolkitWithAvailableStubsAndUnavailableDegradedTools() {
        CatalogToolkitFactory factory = new CatalogToolkitFactory();
        ResolvedCatalog catalog = new ResolvedCatalog("t1", List.of(
                new CatalogTool("weather.ping", "ping", "s1", true, false),
                new CatalogTool("tickets.book", "book", "s2", false, true)));
        Toolkit toolkit = factory.build(catalog);
        // 健康工具走 stub；不可用工具仍注册为 ERROR 降级工具
        assertEquals(2, toolkit.getToolNames().size());
        assertTrue(toolkit.getToolNames().contains("weather_ping"));
        assertTrue(toolkit.getToolNames().contains("tickets_book"));
    }
}
