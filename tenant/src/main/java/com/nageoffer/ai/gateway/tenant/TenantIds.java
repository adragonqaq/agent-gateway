package com.nageoffer.ai.gateway.tenant;

public final class TenantIds {

    private TenantIds() {
    }

    public static String require(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new MissingTenantException("tenant_id is required");
        }
        return tenantId.trim();
    }
}
