package com.agent.gateway.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpAdminServiceTest {

    private McpAdminService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:mcp_admin_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO gw_tenant (id, name) VALUES (?, ?)", "tA", "A");
        JdbcMcpRegistry registry = new JdbcMcpRegistry(jdbc);
        GatewaySecretProperties props = new GatewaySecretProperties();
        props.setMasterKey("0123456789abcdef");
        service = new McpAdminService(registry, props, emptyProvider());
    }

    @Test
    void upsertEncryptsHeadersAndListsOnlyActive() {
        McpServerRecord saved = service.upsert(
                "tA", null, "weather", "http://localhost:8080/mcp",
                Map.of("Authorization", "Bearer secret"), "ACTIVE");
        assertNotNull(saved.id());
        assertTrue(saved.headersCipher() != null && !saved.headersCipher().contains("secret"));
        assertEquals(1, service.listActive("tA").size());

        service.disable("tA", saved.id());
        assertEquals(0, service.listActive("tA").size());
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) throws BeansException {
                return null;
            }

            @Override
            public T getIfAvailable() throws BeansException {
                return null;
            }

            @Override
            public T getIfUnique() throws BeansException {
                return null;
            }

            @Override
            public T getObject() throws BeansException {
                return null;
            }

            @Override
            public Iterator<T> iterator() {
                return Stream.<T>empty().iterator();
            }
        };
    }
}
