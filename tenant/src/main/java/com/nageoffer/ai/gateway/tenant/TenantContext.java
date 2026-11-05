package com.nageoffer.ai.gateway.tenant;

public record TenantContext(
        String tenantId,
        String apiKeyId,
        String role,
        String modelTier,
        String credentialBinding
) {
}
