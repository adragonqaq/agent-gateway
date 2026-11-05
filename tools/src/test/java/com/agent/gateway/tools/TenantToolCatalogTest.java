package com.agent.gateway.tools;

import com.agent.gateway.tenant.PolicyEngine;
import com.agent.gateway.tenant.ToolPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.agent.gateway.tools.mcp.RemoteMcpTool;
import com.agent.gateway.tools.mcp.TenantMcpClientPool;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantToolCatalogTest {

    private JdbcMcpRegistry registry;
    private PolicyEngine policyEngine;
    private ObjectProvider<com.agent.gateway.tools.mcp.TenantMcpClientPool> poolProvider;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:cat_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO gw_tenant (id, name) VALUES ('tA','A')");
        registry = new JdbcMcpRegistry(jdbc);
        policyEngine = new PolicyEngine(jdbc);
        poolProvider = mock(ObjectProvider.class);
        when(poolProvider.getIfAvailable()).thenReturn(null);
    }

    @Test
    void deniedToolExcludedFromCatalog() {
        registry.upsert(new McpServerRecord("s1", "tA", "weather", "http://a", null, "ACTIVE"));
        policyEngine.upsert("tA", new ToolPolicy("weather.ping", "DENY", true));
        TenantToolCatalog catalog = new TenantToolCatalog(registry, policyEngine, poolProvider);
        assertTrue(catalog.resolve("tA").tools().isEmpty());
    }

    @Test
    void unreachableMcpMarkedUnavailable() {
        registry.upsert(new McpServerRecord("s1", "tA", "weather", "http://a", null, "ACTIVE"));
        TenantToolCatalog catalog = new TenantToolCatalog(registry, policyEngine, poolProvider, s -> {
            throw new IllegalStateException("down");
        });
        ResolvedCatalog resolved = catalog.resolve("tA");
        assertEquals(1, resolved.tools().size());
        assertFalse(resolved.tools().get(0).available());
    }

    @Test
    void partialServerFailureKeepsHealthyToolsAndMarksDownServerUnavailable() {
        registry.upsert(new McpServerRecord("s1", "tA", "weather", "http://a", null, "ACTIVE"));
        registry.upsert(new McpServerRecord("s2", "tA", "orders", "http://b", null, "ACTIVE"));
        TenantMcpClientPool pool = mock(TenantMcpClientPool.class);
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("weather_ping")
                .description("ping")
                .inputSchema(new McpSchema.JsonSchema("object", null, null, null, null, null))
                .build();
        when(pool.discover("tA", true)).thenReturn(List.of(
                new RemoteMcpTool("s1", "weather", tool, null, true)));
        when(poolProvider.getIfAvailable()).thenReturn(pool);

        TenantToolCatalog catalog = new TenantToolCatalog(registry, policyEngine, poolProvider);
        ResolvedCatalog resolved = catalog.resolve("tA");
        assertTrue(resolved.tools().stream().anyMatch(t -> "weather_ping".equals(t.name()) && t.available()));
        assertTrue(resolved.tools().stream().anyMatch(t -> t.name().endsWith(".unavailable") && !t.available()));
    }
}
