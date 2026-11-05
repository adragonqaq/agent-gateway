package com.nageoffer.ai.gateway.tenant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcTenantRepositoryTest {

    private JdbcTenantRepository repo;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:tenant_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql"));
        populator.execute(ds);
        repo = new JdbcTenantRepository(new JdbcTemplate(ds));
    }

    @Test
    void findByKeyHashRequiresTenantScopedRow() {
        repo.insertTenant(new TenantRecord("tA", "A", "ACTIVE", "standard", "platform", 5));
        repo.insertTenant(new TenantRecord("tB", "B", "ACTIVE", "standard", "platform", 5));
        repo.insertApiKey(new ApiKeyRecord("k1", "tA", "hashA", "sk_a", "RUNTIME", "ACTIVE"));

        assertTrue(repo.findActiveByKeyHash("hashA").isPresent());
        assertEquals("tA", repo.findActiveByKeyHash("hashA").get().tenantId());
    }

    @Test
    void inactiveTenantKeyNotResolved() {
        repo.insertTenant(new TenantRecord("tA", "A", "DISABLED", "standard", "platform", 5));
        repo.insertApiKey(new ApiKeyRecord("k1", "tA", "hashA", "sk_a", "RUNTIME", "ACTIVE"));
        assertTrue(repo.findActiveByKeyHash("hashA").isEmpty());
    }
}
