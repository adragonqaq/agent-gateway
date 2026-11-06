package com.agent.gateway.tenant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private AuthService auth;
    private JwtService jwt;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:auth_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE");
        ds.setUsername("sa");
        ds.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")).execute(ds);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        JdbcTenantRepository tenants = new JdbcTenantRepository(jdbc);
        JdbcUserRepository users = new JdbcUserRepository(jdbc);
        jwt = new JwtService("unit-test-secret-key-32bytes-min!!", 3600, 86400);
        auth = new AuthService(users, new TenantAdminService(tenants), tenants, jwt);
    }

    @Test
    void registerLoginAndMe() {
        AuthService.AuthTokens tokens = auth.register("Alice@Example.com", "password1");
        assertNotNull(tokens.accessToken());
        String userId = jwt.requireUserId(tokens.accessToken(), "access");
        Map<String, Object> me = auth.me(userId);
        assertEquals("alice@example.com", me.get("email"));
        assertTrue(((java.util.List<?>) me.get("projects")).size() >= 1);

        AuthService.AuthTokens again = auth.login("alice@example.com", "password1");
        assertEquals(userId, jwt.requireUserId(again.accessToken(), "access"));

        assertThrows(UnauthorizedException.class, () -> auth.login("alice@example.com", "wrong-pass"));
    }

    @Test
    void resolveJwtRequiresMembership() {
        AuthService.AuthTokens tokens = auth.register("bob@example.com", "password1");
        String userId = jwt.requireUserId(tokens.accessToken(), "access");
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> projects =
                (java.util.List<Map<String, Object>>) auth.me(userId).get("projects");
        String projectId = String.valueOf(projects.get(0).get("id"));
        TenantContext ctx = auth.resolveJwtContext(userId, projectId);
        assertEquals("owner", ctx.memberRole());
        assertEquals("JWT", ctx.authKind());
        assertThrows(UnauthorizedException.class, () -> auth.resolveJwtContext(userId, "ten_missing"));
    }
}
