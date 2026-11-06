package com.agent.gateway.observe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotaServiceTest {

    private JdbcTemplate jdbc;
    private UsageService usage;
    private QuotaService quota;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:quota_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        usage = new UsageService(jdbc);
        quota = new QuotaService(jdbc, usage);
        jdbc.update("""
                INSERT INTO gw_tenant (id, name, status, model_tier, credential_binding, max_concurrent)
                VALUES ('tA', 'A', 'ACTIVE', 'standard', 'platform', 5)
                """);
    }

    @Test
    void unlimitedWhenQuotaNull() {
        assertNull(quota.tokenQuota("tA"));
        quota.requireTokenQuotaAvailable("tA");
        Map<String, Object> snap = quota.snapshot("tA");
        assertTrue((Boolean) snap.get("unlimited"));
        assertNull(snap.get("tokenQuota"));
    }

    @Test
    void blocksWhenUsedReachesQuota() {
        quota.setTokenQuota("tA", 100L);
        usage.record("tA", "task1", "total_tokens", 100);
        QuotaExceededException ex = assertThrows(
                QuotaExceededException.class,
                () -> quota.requireTokenQuotaAvailable("tA"));
        assertEquals(100L, ex.used());
        assertEquals(100L, ex.quota());
        Map<String, Object> snap = quota.snapshot("tA");
        assertEquals(0L, snap.get("tokenRemaining"));
    }

    @Test
    void allowsWhenUnderQuota() {
        quota.setTokenQuota("tA", 200L);
        usage.record("tA", "task1", "total_tokens", 50);
        quota.requireTokenQuotaAvailable("tA");
        assertEquals(150L, quota.snapshot("tA").get("tokenRemaining"));
    }
}
