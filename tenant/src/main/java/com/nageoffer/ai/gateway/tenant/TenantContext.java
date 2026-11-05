package com.nageoffer.ai.gateway.tenant;

public record TenantContext(
        String tenantId,
        String apiKeyId,
        String modelTier,
        String credentialBinding
) {
}
