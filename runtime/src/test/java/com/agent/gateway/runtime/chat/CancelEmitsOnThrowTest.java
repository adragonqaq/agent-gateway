package com.agent.gateway.runtime.chat;

import com.agent.gateway.memory.JdbcSessionStateStore;
import com.agent.gateway.observe.AuditService;
import com.agent.gateway.observe.UsageService;
import com.agent.gateway.runtime.ConfirmGate;
import com.agent.gateway.runtime.InMemoryRunGate;
import com.agent.gateway.runtime.PendingConfirmation;
import com.agent.gateway.tenant.PolicyEngine;
import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tools.JdbcMcpRegistry;
import com.agent.gateway.tools.ResolvedCatalog;
import com.agent.gateway.tools.TenantToolCatalog;
import com.agent.gateway.tools.mcp.TenantMcpClientPool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CancelEmitsOnThrowTest {

    @Test
    void cancelEmitsCancelWhenExecutorThrowsAfterInterrupt() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:cancel_throw_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO gw_tenant (id, name) VALUES (?, ?)", "tA", "A");
        ConfirmGate confirmGate = new ConfirmGate(jdbc);

        AgentExecutor boom = new AgentExecutor() {
            @Override
            public void run(TenantContext tenant, String taskId, String conversationId, String message,
                            ResolvedCatalog catalog, String priorState,
                            Consumer<AgentEvent> events, Consumer<String> stateSaver) {
                events.accept(new AgentEvent("thinking", "x"));
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                stateSaver.accept("{\"interrupted\":true}");
                throw new IllegalStateException("interrupted by cancel");
            }

            @Override
            public void resumeConfirm(TenantContext tenant, String resumeTaskId, List<PendingConfirmation> settled,
                                      boolean approved, ResolvedCatalog catalog,
                                      Consumer<AgentEvent> events, Consumer<String> stateSaver) {
            }

            @Override
            public Runnable interruptAction(String tenantId, String conversationId) {
                return () -> {
                };
            }
        };
        @SuppressWarnings("unchecked")
        ObjectProvider<TenantMcpClientPool> pool = mock(ObjectProvider.class);
        when(pool.getIfAvailable()).thenReturn(null);
        AgentChatService chatService = new AgentChatService(
                new InMemoryRunGate(),
                new TenantToolCatalog(new JdbcMcpRegistry(jdbc), new PolicyEngine(jdbc), pool),
                new JdbcSessionStateStore(jdbc),
                boom,
                new CatalogFingerprintTracker(),
                new AuditService(jdbc),
                new UsageService(jdbc),
                new com.agent.gateway.observe.QuotaService(jdbc, new UsageService(jdbc)),
                confirmGate,
                new ActiveRunRegistry(),
                new com.agent.gateway.observe.SpanRecorder(jdbc),
                new com.agent.gateway.observe.TraceQueryService(jdbc));

        TenantContext tenant = new TenantContext("tA", "keyA", "RUNTIME", "standard", "platform");
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<String> taskIdRef = new AtomicReference<>();
        List<String> types = new ArrayList<>();
        Thread worker = new Thread(() -> chatService.startStreaming(
                tenant, "conv", "hi", 5,
                ready -> {
                    taskIdRef.set(ready.taskId());
                    started.countDown();
                },
                event -> types.add(event.type())));
        worker.start();
        assertTrue(started.await(2, TimeUnit.SECONDS));
        Thread.sleep(50);
        chatService.cancel("tA", taskIdRef.get());
        worker.join(3000);
        assertTrue(types.contains("cancel"), "types=" + types);
        assertTrue(jdbc.queryForObject(
                "SELECT COUNT(*) FROM gw_agent_state WHERE tenant_id = ? AND conversation_id = ?",
                Integer.class, "tA", "conv") > 0);
    }
}
