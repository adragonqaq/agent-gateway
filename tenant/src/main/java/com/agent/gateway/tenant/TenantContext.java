package com.agent.gateway.tenant;

/**
 * 请求级租户上下文：鉴权通过后贯穿数据面与工具调用。
 * 属于 tenant 模块；由 {@link TenantResolver} 产出，后续读写必须携带 {@code tenantId}。
 * <ul>
 *   <li>{@code tenantId} — 租户主键</li>
 *   <li>{@code apiKeyId} — 本次鉴权使用的 Key ID</li>
 *   <li>{@code role} — Key 角色</li>
 *   <li>{@code modelTier} — 模型档位（计费/路由）</li>
 *   <li>{@code credentialBinding} — 凭证绑定策略（如 platform）</li>
 * </ul>
 */
public record TenantContext(
        String tenantId,
        String apiKeyId,
        String role,
        String modelTier,
        String credentialBinding
) {
}
