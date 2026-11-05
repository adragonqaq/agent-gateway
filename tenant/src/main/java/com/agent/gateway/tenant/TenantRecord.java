package com.agent.gateway.tenant;

/**
 * 租户主数据记录。
 * 属于 tenant 模块。
 * <ul>
 *   <li>{@code id} — 租户主键（如 ten_xxx）</li>
 *   <li>{@code name} — 展示名</li>
 *   <li>{@code status} — ACTIVE 等；非 ACTIVE 不可进数据面</li>
 *   <li>{@code modelTier} — 默认模型档位</li>
 *   <li>{@code credentialBinding} — 模型凭证绑定方式</li>
 *   <li>{@code maxConcurrent} — 并发上限</li>
 * </ul>
 */
public record TenantRecord(
        String id,
        String name,
        String status,
        String modelTier,
        String credentialBinding,
        int maxConcurrent
) {
}
