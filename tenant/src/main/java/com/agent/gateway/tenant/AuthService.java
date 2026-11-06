package com.agent.gateway.tenant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 注册 / 登录 / me：用户 + 默认项目 + JWT。
 */
@Service
public class AuthService {

    private final JdbcUserRepository users;
    private final TenantAdminService tenants;
    private final TenantRepository tenantRepository;
    private final JwtService jwtService;

    public AuthService(
            JdbcUserRepository users,
            TenantAdminService tenants,
            TenantRepository tenantRepository,
            JwtService jwtService) {
        this.users = users;
        this.tenants = tenants;
        this.tenantRepository = tenantRepository;
        this.jwtService = jwtService;
    }

    /**
     * 注册并返回 access/refresh token。
     */
    @Transactional
    public AuthTokens register(String email, String password) {
        String normalized = JdbcUserRepository.normalizeEmail(email);
        if (normalized.isBlank() || !normalized.contains("@")) {
            throw new IllegalArgumentException("invalid email");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("password must be at least 8 characters");
        }
        if (users.findByEmail(normalized).isPresent()) {
            throw new IllegalArgumentException("email already registered");
        }
        String userId = "usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        users.insertUser(new UserRecord(userId, normalized, PasswordHasher.hash(password), "ACTIVE"));
        String projectName = normalized.contains("@")
                ? normalized.substring(0, normalized.indexOf('@')) + "'s project"
                : "My project";
        TenantRecord project = tenants.createTenant(projectName, "standard", "platform", 5);
        users.insertMember(new ProjectMemberRecord(project.id(), userId, "owner"));
        return tokensFor(userId, normalized);
    }

    public AuthTokens login(String email, String password) {
        String normalized = JdbcUserRepository.normalizeEmail(email);
        UserRecord user = users.findByEmail(normalized)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            throw new UnauthorizedException("User disabled");
        }
        if (!PasswordHasher.matches(password, user.passwordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return tokensFor(user.id(), user.email());
    }

    public AuthTokens refresh(String refreshToken) {
        String userId = jwtService.requireUserId(refreshToken, "refresh");
        UserRecord user = users.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Invalid token"));
        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            throw new UnauthorizedException("User disabled");
        }
        return tokensFor(user.id(), user.email());
    }

    public Map<String, Object> me(String userId) {
        UserRecord user = users.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        List<Map<String, Object>> projects = new ArrayList<>();
        for (ProjectMemberRecord m : users.listMemberships(userId)) {
            tenantRepository.findTenant(m.tenantId()).ifPresent(t -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", t.id());
                row.put("name", t.name());
                row.put("role", m.role());
                projects.add(row);
            });
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", user.id());
        out.put("email", user.email());
        out.put("projects", projects);
        return out;
    }

    /**
     * 将 JWT 用户解析为带项目的 {@link TenantContext}。
     */
    public TenantContext resolveJwtContext(String userId, String projectId) {
        UserRecord user = users.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        if (!"ACTIVE".equalsIgnoreCase(user.status())) {
            throw new UnauthorizedException("User disabled");
        }
        String tid = TenantIds.require(projectId);
        ProjectMemberRecord member = users.findMember(tid, userId)
                .orElseThrow(() -> new UnauthorizedException("Not a member of this project"));
        TenantRecord tenant = tenantRepository.findTenant(tid)
                .orElseThrow(() -> new UnauthorizedException("Project not found"));
        if (!"ACTIVE".equalsIgnoreCase(tenant.status())) {
            throw new UnauthorizedException("Project not active");
        }
        return new TenantContext(
                tenant.id(),
                null,
                member.role(),
                tenant.modelTier(),
                tenant.credentialBinding(),
                user.id(),
                member.role(),
                "JWT");
    }

    private AuthTokens tokensFor(String userId, String email) {
        return new AuthTokens(
                jwtService.issueAccessToken(userId, email),
                jwtService.issueRefreshToken(userId, email));
    }

    public record AuthTokens(String accessToken, String refreshToken) {
    }
}
