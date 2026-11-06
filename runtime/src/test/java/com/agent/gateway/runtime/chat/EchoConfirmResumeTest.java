package com.agent.gateway.runtime.chat;

import com.agent.gateway.memory.JdbcSessionStateStore;
import com.agent.gateway.observe.AuditService;
import com.agent.gateway.observe.UsageService;
import com.agent.gateway.runtime.ConfirmDecision;
import com.agent.gateway.runtime.ConfirmGate;
import com.agent.gateway.runtime.ExpiredConfirmationException;
import com.agent.gateway.runtime.InMemoryRunGate;
import com.agent.gateway.tenant.PolicyEngine;
import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tools.JdbcMcpRegistry;
import com.agent.gateway.tools.McpServerRecord;
import com.agent.gateway.tools.TenantToolCatalog;
import com.agent.gateway.tools.mcp.TenantMcpClientPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EchoConfirmResumeTest {

    private AgentChatService chatService;
    private ConfirmGate confirmGate;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:echo_cfm_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO gw_tenant (id, name) VALUES (?, ?)", "tA", "A");
        confirmGate = new ConfirmGate(jdbc);
        JdbcMcpRegistry mcpRegistry = new JdbcMcpRegistry(jdbc);
        mcpRegistry.upsert(new McpServerRecord("s1", "tA", "orders", "http://orders", null, "ACTIVE"));
        @SuppressWarnings("unchecked")
        ObjectProvider<TenantMcpClientPool> pool = mock(ObjectProvider.class);
        when(pool.getIfAvailable()).thenReturn(null);
        TenantToolCatalog catalog = new TenantToolCatalog(mcpRegistry, new PolicyEngine(jdbc), pool);
        // 默认 fallback 工具 requireConfirm=true
        chatService = new AgentChatService(
                new InMemoryRunGate(),
                catalog,
                new JdbcSessionStateStore(jdbc),
                new EchoAgentExecutor(new CatalogFingerprintTracker(), confirmGate),
                new CatalogFingerprintTracker(),
                new AuditService(jdbc),
                new UsageService(jdbc),
                confirmGate,
                new ActiveRunRegistry(),
                new com.agent.gateway.observe.SpanRecorder(jdbc),
                new com.agent.gateway.observe.TraceQueryService(jdbc));
    }

    @Test
    void approveResumesWithConfirmedAnswer() {
        TenantContext tenant = new TenantContext("tA", "keyA", "RUNTIME", "standard", "platform");
        AgentChatService.ChatRun first = chatService.start(tenant, "conv_1", "please delete", 5);
        assertTrue(first.events().stream().anyMatch(e -> "confirm".equals(e.type())));
        String confirmId = jdbc.queryForObject(
                "SELECT id FROM gw_confirm WHERE tenant_id = ? AND status = ?",
                String.class, "tA", ConfirmDecision.PENDING.name());

        AgentChatService.ChatRun resumed = chatService.resumeAfterConfirm(tenant, confirmId, true, 5);
        assertTrue(resumed.events().stream().anyMatch(e -> "answer".equals(e.type()) && e.data().startsWith("confirmed:")));
        assertEquals(ConfirmDecision.CONFIRMED, confirmGate.get("tA", confirmId).status());
    }

    @Test
    void expiredConfirmCannotResume() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        ConfirmGate shortGate = new ConfirmGate(jdbc, clock, Duration.ofSeconds(5));
        // 替换服务内闸门：直接挂起一张短超时确认单
        String confirmId = shortGate.suspend("tA", "task_exp", "conv_exp", "delete", "{}");
        now.set(Instant.parse("2026-01-01T00:01:00Z"));
        AgentChatService service = new AgentChatService(
                new InMemoryRunGate(),
                new TenantToolCatalog(new JdbcMcpRegistry(jdbc), new PolicyEngine(jdbc), mockPool()),
                new JdbcSessionStateStore(jdbc),
                new EchoAgentExecutor(new CatalogFingerprintTracker(), shortGate),
                new CatalogFingerprintTracker(),
                new AuditService(jdbc),
                new UsageService(jdbc),
                shortGate,
                new ActiveRunRegistry(),
                new com.agent.gateway.observe.SpanRecorder(jdbc),
                new com.agent.gateway.observe.TraceQueryService(jdbc));
        TenantContext tenant = new TenantContext("tA", "keyA", "RUNTIME", "standard", "platform");
        assertThrows(ExpiredConfirmationException.class,
                () -> service.resumeAfterConfirm(tenant, confirmId, true, 5));
        Integer audits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM gw_audit_event WHERE tenant_id = ? AND event_type = ?",
                Integer.class, "tA", "CONFIRM_EXPIRED");
        assertEquals(1, audits);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<TenantMcpClientPool> mockPool() {
        ObjectProvider<TenantMcpClientPool> pool = mock(ObjectProvider.class);
        when(pool.getIfAvailable()).thenReturn(null);
        return pool;
    }
}
