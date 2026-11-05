package com.nageoffer.ai.gateway.runtime;

import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfirmGateTest {

    private JdbcTemplate jdbc;
    private AtomicReference<Instant> now;
    private Clock clock;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:cfm_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
    }

    @Test
    void timeoutBecomesDenied() {
        ConfirmGate gate = new ConfirmGate(jdbc, clock, Duration.ofSeconds(10));
        String id = gate.suspend("tA", "task1", "delete_order", "{}");
        now.set(Instant.parse("2026-01-01T00:01:00Z"));
        assertEquals(ConfirmDecision.EXPIRED, gate.get("tA", id).status());
    }

    @Test
    void otherTenantCannotConfirm() {
        ConfirmGate gate = new ConfirmGate(jdbc, clock, Duration.ofSeconds(300));
        String id = gate.suspend("tA", "task1", "delete_order", "{}");
        assertThrows(NotFoundException.class, () -> gate.confirm("tB", id));
    }
}
