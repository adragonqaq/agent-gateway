package com.agent.gateway.runtime.chat;

import com.agent.gateway.memory.JdbcSessionStateStore;
import com.agent.gateway.observe.AuditService;
import com.agent.gateway.observe.SpanKind;
import com.agent.gateway.observe.SpanRecord;
import com.agent.gateway.observe.SpanRecorder;
import com.agent.gateway.observe.TraceQueryService;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * echo 一轮对话后应按 traceId 查到 RUN span。
 */
class ChatTraceIntegrationTest {

    private AgentChatService chatService;
    private TraceQueryService traceQuery;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:chat_trace_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO gw_tenant (id, name) VALUES (?, ?)", "tA", "A");
        ConfirmGate confirmGate = new ConfirmGate(jdbc);
        @SuppressWarnings("unchecked")
        ObjectProvider<TenantMcpClientPool> pool = mock(ObjectProvider.class);
        when(pool.getIfAvailable()).thenReturn(null);
        SpanRecorder recorder = new SpanRecorder(jdbc);
        traceQuery = new TraceQueryService(jdbc);
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
                recorder,
                traceQuery);
    }

    @Test
    void echoChatPersistsRunSpanUnderTraceId() {
        TenantContext tenant = new TenantContext("tA", "keyA", "RUNTIME", "standard", "platform");
        AgentChatService.ChatRun run = chatService.start(tenant, "conv_trace", "hello", 5);
        assertNotNull(run.traceId());
        assertTrue(run.traceId().startsWith("trc_"));

        List<SpanRecord> spans = traceQuery.listSpans("tA", run.traceId());
        assertFalse(spans.isEmpty());
        assertTrue(spans.stream().anyMatch(s -> SpanKind.RUN.name().equals(s.kind())));
        assertEquals("OK", spans.stream()
                .filter(s -> "chat.run".equals(s.name()))
                .findFirst()
                .orElseThrow()
                .status());
    }
}
