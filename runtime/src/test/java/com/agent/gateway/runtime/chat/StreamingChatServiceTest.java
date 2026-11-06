package com.agent.gateway.runtime.chat;

import com.agent.gateway.memory.JdbcSessionStateStore;
import com.agent.gateway.observe.AuditService;
import com.agent.gateway.observe.UsageService;
import com.agent.gateway.runtime.ConfirmGate;
import com.agent.gateway.runtime.InMemoryRunGate;
import com.agent.gateway.tenant.PolicyEngine;
import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tools.JdbcMcpRegistry;
import com.agent.gateway.tools.TenantToolCatalog;
import com.agent.gateway.tools.mcp.TenantMcpClientPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StreamingChatServiceTest {

    private AgentChatService chatService;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:stream_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO gw_tenant (id, name) VALUES (?, ?)", "tA", "A");
        ConfirmGate confirmGate = new ConfirmGate(jdbc);
        @SuppressWarnings("unchecked")
        ObjectProvider<TenantMcpClientPool> pool = mock(ObjectProvider.class);
        when(pool.getIfAvailable()).thenReturn(null);
        chatService = new AgentChatService(
                new InMemoryRunGate(),
                new TenantToolCatalog(new JdbcMcpRegistry(jdbc), new PolicyEngine(jdbc), pool),
                new JdbcSessionStateStore(jdbc),
                new EchoAgentExecutor(new CatalogFingerprintTracker(), confirmGate),
                new CatalogFingerprintTracker(),
                new AuditService(jdbc),
                new UsageService(jdbc),
                new com.agent.gateway.observe.QuotaService(jdbc, new UsageService(jdbc)),
                confirmGate,
                new ActiveRunRegistry(),
                new com.agent.gateway.observe.SpanRecorder(jdbc),
                new com.agent.gateway.observe.TraceQueryService(jdbc));
    }

    @Test
    void onTaskReadyFiresBeforeEvents() {
        TenantContext tenant = new TenantContext("tA", "keyA", "RUNTIME", "standard", "platform");
        List<String> order = new ArrayList<>();
        AtomicBoolean ready = new AtomicBoolean(false);
        chatService.startStreaming(tenant, "conv", "hi", 5, runReady -> {
            ready.set(true);
            order.add("ready");
        }, event -> {
            assertTrue(ready.get(), "meta/taskId must be ready before events");
            order.add(event.type());
        });
        assertFalse(order.isEmpty());
        assertTrue(order.get(0).equals("ready"));
    }
}
