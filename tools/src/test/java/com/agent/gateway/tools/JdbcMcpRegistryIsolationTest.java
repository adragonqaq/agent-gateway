package com.agent.gateway.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcMcpRegistryIsolationTest {

    private JdbcMcpRegistry registry;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:mcp_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO gw_tenant (id, name) VALUES (?, ?)", "tA", "A");
        jdbc.update("INSERT INTO gw_tenant (id, name) VALUES (?, ?)", "tB", "B");
        registry = new JdbcMcpRegistry(jdbc);
    }

    @Test
    void listForTenantNeverReturnsOtherTenantServers() {
        registry.upsert(new McpServerRecord("s1", "tA", "weather", "http://a", null, "ACTIVE"));
        registry.upsert(new McpServerRecord("s2", "tB", "tickets", "http://b", null, "ACTIVE"));
        assertEquals(1, registry.listActive("tA").size());
        assertEquals("weather", registry.listActive("tA").get(0).name());
    }
}
