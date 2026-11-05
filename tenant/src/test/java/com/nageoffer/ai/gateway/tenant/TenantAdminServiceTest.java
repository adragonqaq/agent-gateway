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

class TenantAdminServiceTest {

    private TenantAdminService service;
    private TenantResolver resolver;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:admin_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        JdbcTenantRepository repo = new JdbcTenantRepository(new JdbcTemplate(ds));
        service = new TenantAdminService(repo);
        resolver = new TenantResolver(repo);
    }

    @Test
    void createTenantAndIssueKeyRoundTrip() {
        TenantRecord tenant = service.createTenant("Acme", "standard", "platform", 3);
        IssuedApiKey key = service.issueApiKey(tenant.id(), "ADMIN");
        TenantContext ctx = resolver.resolve(key.rawKey());
        assertEquals(tenant.id(), ctx.tenantId());
        assertEquals("ADMIN", ctx.role());
        assertTrue(key.rawKey().startsWith("sk_"));
    }
}
