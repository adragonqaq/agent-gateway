package com.agent.gateway.api.admin;

import com.agent.gateway.api.auth.TenantContextHolder;
import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tenant.UnauthorizedException;

/**
 * 管理面授权：平台 ADMIN Key，或 JWT 下目标项目的 owner。
 */
public final class AdminAccess {

    private AdminAccess() {
    }

    /**
     * 仅平台 ADMIN Key（开户等跨项目操作）。
     */
    public static void requirePlatformAdmin() {
        TenantContext ctx = TenantContextHolder.require();
        if (!"ADMIN".equalsIgnoreCase(ctx.role()) || ctx.isJwt()) {
            throw new UnauthorizedException("Platform admin role required");
        }
    }

    /**
     * 平台 ADMIN Key，或 JWT owner 且当前项目等于 {@code tenantId}。
     *
     * @param tenantId 路径上的目标租户
     */
    public static void requireAdminOrProjectOwner(String tenantId) {
        TenantContext ctx = TenantContextHolder.require();
        if ("ADMIN".equalsIgnoreCase(ctx.role()) && !ctx.isJwt()) {
            return;
        }
        if (ctx.isJwt() && ctx.isProjectOwner() && tenantId != null && tenantId.equals(ctx.tenantId())) {
            return;
        }
        throw new UnauthorizedException("Admin or project owner required");
    }

    /**
     * 当前项目成员（JWT）或任意有效 Key（API Key 路径）。
     *
     * @param projectId 路径项目 ID
     */
    public static void requireProjectMember(String projectId) {
        TenantContext ctx = TenantContextHolder.require();
        if (projectId == null || !projectId.equals(ctx.tenantId())) {
            throw new UnauthorizedException("Not a member of this project");
        }
    }
}
