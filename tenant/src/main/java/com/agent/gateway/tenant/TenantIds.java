package com.agent.gateway.tenant;

/**
 * 租户 ID 校验工具。
 * 属于 tenant 模块；所有持久化读写入口应先 {@link #require(String)}，杜绝空/空白 tenant_id 导致串租。
 */
public final class TenantIds {

    /**
     * 工具类不可实例化。
     */
    private TenantIds() {
    }

    /**
     * 校验并规范化租户 ID。
     *
     * @param tenantId 原始租户 ID
     * @return trim 后的非空 ID
     * @throws MissingTenantException 为 null 或空白时
     */
    public static String require(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new MissingTenantException("tenant_id is required");
        }
        return tenantId.trim();
    }
}
