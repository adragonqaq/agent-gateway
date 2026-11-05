package com.agent.gateway.tenant;

public record TenantContext(
        String tenantId,
        String apiKeyId,
        String role,
        String modelTier,
        String credentialBinding
) {
}
