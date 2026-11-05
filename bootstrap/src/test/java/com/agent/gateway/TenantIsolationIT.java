package com.agent.gateway;

import com.agent.gateway.memory.JdbcSessionStateStore;
import com.agent.gateway.observe.AuditService;
import com.agent.gateway.observe.UsageService;
import com.agent.gateway.runtime.ConfirmGate;
import com.agent.gateway.runtime.InMemoryRunGate;
import com.agent.gateway.runtime.NotFoundException;
import com.agent.gateway.runtime.chat.AgentChatService;
import com.agent.gateway.runtime.chat.EchoAgentExecutor;
import com.agent.gateway.runtime.chat.CatalogFingerprintTracker;
import com.agent.gateway.tenant.ApiKeyHasher;
import com.agent.gateway.tenant.ApiKeyRecord;
import com.agent.gateway.tenant.JdbcTenantRepository;
import com.agent.gateway.tenant.PolicyEngine;
import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tenant.TenantRecord;
import com.agent.gateway.tenant.TenantResolver;
import com.agent.gateway.tools.JdbcMcpRegistry;
import com.agent.gateway.tools.McpServerRecord;
import com.agent.gateway.tools.TenantToolCatalog;
import com.agent.gateway.tools.mcp.TenantMcpClientPool;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantIsolationIT {

    private JdbcTemplate jdbc;
    private TenantResolver resolver;
    private JdbcSessionStateStore stateStore;
    private ConfirmGate confirmGate;
    private InMemoryRunGate runGate;
    private AgentChatService chatService;
    private JdbcMcpRegistry mcpRegistry;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:iso_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        JdbcTenantRepository tenantRepo = new JdbcTenantRepository(jdbc);
        resolver = new TenantResolver(tenantRepo);
        stateStore = new JdbcSessionStateStore(jdbc);
        confirmGate = new ConfirmGate(jdbc);
        runGate = new InMemoryRunGate();
        mcpRegistry = new JdbcMcpRegistry(jdbc);
        @SuppressWarnings("unchecked")
        ObjectProvider<TenantMcpClientPool> poolProvider = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(poolProvider.getIfAvailable()).thenReturn(null);
        TenantToolCatalog catalog = new TenantToolCatalog(mcpRegistry, new PolicyEngine(jdbc), poolProvider);
        chatService = new AgentChatService(
                runGate, catalog, stateStore, new EchoAgentExecutor(new CatalogFingerprintTracker(), confirmGate), new CatalogFingerprintTracker(),
                new AuditService(jdbc), new UsageService(jdbc), confirmGate);

        tenantRepo.insertTenant(new TenantRecord("tA", "A", "ACTIVE", "standard", "platform", 5));
        tenantRepo.insertTenant(new TenantRecord("tB", "B", "ACTIVE", "standard", "platform", 5));
        String rawA = "sk_tenant_a_key";
        String rawB = "sk_tenant_b_key";
        tenantRepo.insertApiKey(new ApiKeyRecord("kA", "tA", ApiKeyHasher.sha256Hex(rawA), "sk_tenant", "RUNTIME", "ACTIVE"));
        tenantRepo.insertApiKey(new ApiKeyRecord("kB", "tB", ApiKeyHasher.sha256Hex(rawB), "sk_tenant", "RUNTIME", "ACTIVE"));
    }

    @Test
    void apiKeyNeverResolvesAcrossTenants() {
        assertEquals("tA", resolver.resolve("sk_tenant_a_key").tenantId());
        assertEquals("tB", resolver.resolve("sk_tenant_b_key").tenantId());
    }

    @Test
    void stateAndMcpAndConfirmAreIsolated() {
        stateStore.save("tA", "c1", "{\"x\":1}");
        assertTrue(stateStore.load("tB", "c1").isEmpty());

        mcpRegistry.upsert(new McpServerRecord("s1", "tA", "weather", "http://a", null, "ACTIVE"));
        assertEquals(0, mcpRegistry.listActive("tB").size());

        String confirmId = confirmGate.suspend("tA", "task1", "conv1", "delete", "{}");
        assertThrows(NotFoundException.class, () -> confirmGate.confirm("tB", confirmId));
    }

    @Test
    void runGateSaturationDoesNotBlockOtherTenant() {
        for (int i = 0; i < 5; i++) {
            assertTrue(runGate.tryAcquire("tA", "a" + i, 5).acquired());
        }
        assertTrue(runGate.tryAcquire("tB", "b1", 5).acquired());
    }

    @Test
    void cancelForeignTaskNotFound() {
        TenantContext a = resolver.resolve("sk_tenant_a_key");
        var run = chatService.start(a, "c1", "hello", 5);
        assertThrows(NotFoundException.class, () -> chatService.cancel("tB", run.taskId()));
    }
}
