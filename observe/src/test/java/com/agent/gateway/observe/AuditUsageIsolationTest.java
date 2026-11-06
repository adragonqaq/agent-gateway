package com.agent.gateway.observe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditUsageIsolationTest {

    private AuditService audit;
    private UsageService usage;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:obs_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        audit = new AuditService(jdbc);
        usage = new UsageService(jdbc);
    }

    @Test
    void eventsStayInTenant() {
        audit.record("tA", "CONFIRM", "x", "{}");
        usage.record("tA", "task1", "tokens", 10);
        assertEquals(1, audit.countForTenant("tA"));
        assertEquals(0, audit.countForTenant("tB"));
        assertEquals(10, usage.sumForTenant("tA", "tokens"));
        assertEquals(0, usage.sumForTenant("tB", "tokens"));
    }

    @Test
    void listRecentDoesNotLeakAcrossTenants() {
        audit.record("tA", "CHAT_COMPLETE", "taskA", "{}");
        audit.record("tB", "CHAT_COMPLETE", "taskB", "{}");
        List<Map<String, Object>> a = audit.listRecent("tA", 10);
        List<Map<String, Object>> b = audit.listRecent("tB", 10);
        assertEquals(1, a.size());
        assertEquals("taskA", a.get(0).get("ref_id"));
        assertEquals(1, b.size());
        assertEquals("taskB", b.get(0).get("ref_id"));
    }

    @Test
    void usageSummaryIsTenantScoped() {
        usage.record("tA", "t1", "runs", 2);
        usage.record("tA", "t2", "tokens", 5);
        usage.record("tB", "t3", "runs", 9);
        List<Map<String, Object>> a = usage.summarizeForTenant("tA");
        assertEquals(2, a.size());
        assertTrue(a.stream().anyMatch(row -> "runs".equals(row.get("metric"))
                && ((Number) row.get("total")).longValue() == 2L));
        List<Map<String, Object>> b = usage.summarizeForTenant("tB");
        assertEquals(1, b.size());
        assertEquals(9L, ((Number) b.get(0).get("total")).longValue());
    }

    @Test
    void usageForTaskIsScopedAndAggregated() {
        usage.record("tA", "taskX", "prompt_tokens", 100);
        usage.record("tA", "taskX", "completion_tokens", 20);
        usage.record("tA", "taskY", "prompt_tokens", 9);
        usage.record("tB", "taskX", "prompt_tokens", 999);
        Map<String, Long> x = usage.summarizeForTask("tA", "taskX");
        assertEquals(100L, x.get("prompt_tokens"));
        assertEquals(20L, x.get("completion_tokens"));
        assertTrue(usage.summarizeForTask("tB", "taskX").containsKey("prompt_tokens"));
        assertEquals(999L, usage.summarizeForTask("tB", "taskX").get("prompt_tokens"));
        assertTrue(usage.summarizeForTask("tA", "missing").isEmpty());
    }
}
