package com.agent.gateway.observe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
