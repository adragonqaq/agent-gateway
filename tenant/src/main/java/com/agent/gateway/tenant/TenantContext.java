package com.agent.gateway.tenant;

/**
 * 请求级租户上下文：鉴权通过后贯穿数据面与工具调用。
 * 属于 tenant 模块；由 API Key 或 JWT 过滤器产出。
 * <ul>
 *   <li>{@code tenantId} — 项目/租户主键</li>
 *   <li>{@code apiKeyId} — API Key 路径下的 Key ID；JWT 路径为 null</li>
 *   <li>{@code role} — Key 角色（ADMIN/RUNTIME）或兼容字段</li>
 *   <li>{@code modelTier} — 模型档位</li>
 *   <li>{@code credentialBinding} — 凭证绑定</li>
 *   <li>{@code userId} — JWT 用户；API Key 路径 null</li>
 *   <li>{@code memberRole} — 项目成员角色 owner/member；Key 路径 null</li>
 *   <li>{@code authKind} — {@code API_KEY} 或 {@code JWT}</li>
 * </ul>
 */
public record TenantContext(
        String tenantId,
        String apiKeyId,
        String role,
        String modelTier,
        String credentialBinding,
        String userId,
        String memberRole,
        String authKind
) {
    /**
     * API Key 路径兼容构造（无用户）。
     */
    public TenantContext(
            String tenantId,
            String apiKeyId,
            String role,
            String modelTier,
            String credentialBinding) {
        this(tenantId, apiKeyId, role, modelTier, credentialBinding, null, null, "API_KEY");
    }

    /** 是否 JWT 用户会话。 */
    public boolean isJwt() {
        return "JWT".equalsIgnoreCase(authKind);
    }

    /** 是否项目 owner（含平台 ADMIN Key）。 */
    public boolean isProjectOwner() {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return true;
        }
        return "owner".equalsIgnoreCase(memberRole);
    }
}
