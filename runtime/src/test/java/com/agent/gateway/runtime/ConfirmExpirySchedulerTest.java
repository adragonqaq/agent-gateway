package com.agent.gateway.runtime;

import com.agent.gateway.observe.AuditService;
import com.agent.gateway.runtime.config.GatewayConfirmProperties;
import org.junit.jupiter.api.Test;
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

class ConfirmExpirySchedulerTest {

    @Test
    void sweepWritesAuditForExpired() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:cfm_sw_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        ConfirmGate gate = new ConfirmGate(jdbc, clock, Duration.ofSeconds(1));
        String id = gate.suspend("tA", "task1", "conv1", "delete", "{}");
        now.set(Instant.parse("2026-01-01T00:01:00Z"));
        GatewayConfirmProperties props = new GatewayConfirmProperties();
        props.setSweepBatchSize(50);
        ConfirmExpiryScheduler scheduler = new ConfirmExpiryScheduler(gate, new AuditService(jdbc), props);
        scheduler.sweepExpired();
        assertEquals(ConfirmDecision.EXPIRED, gate.get("tA", id).status());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM gw_audit_event WHERE event_type = ? AND ref_id = ?",
                Integer.class, "CONFIRM_EXPIRED", id));
    }
}
