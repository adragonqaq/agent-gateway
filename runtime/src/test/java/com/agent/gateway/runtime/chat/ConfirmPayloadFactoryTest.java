package com.agent.gateway.runtime.chat;

import com.agent.gateway.runtime.ConfirmGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmPayloadFactoryTest {

    private ConfirmGate confirmGate;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:cfm_payload_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO gw_tenant (id, name) VALUES (?, ?)", "tA", "A");
        confirmGate = new ConfirmGate(jdbc);
    }

    @Test
    void suspendAndSerializeContainsConfirmId() {
        String json = ConfirmPayloadFactory.suspendAndSerialize(
                confirmGate, "tA", "task_1", "reply_1",
                List.of(new ConfirmPayloadFactory.ToolCall("delete_order", "{\"id\":1}")));
        assertTrue(json.contains("\"confirmId\""));
        assertTrue(json.contains("delete_order"));
        assertTrue(json.contains("task_1"));
        assertTrue(json.contains("reply_1"));
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM gw_confirm WHERE tenant_id = ? AND tool_name = ?",
                Integer.class, "tA", "delete_order");
        assertTrue(count != null && count == 1);
    }
}
