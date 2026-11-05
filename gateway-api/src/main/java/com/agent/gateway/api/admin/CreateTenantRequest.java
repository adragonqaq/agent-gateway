package com.agent.gateway.api.admin;

public record CreateTenantRequest(
        String name,
        String modelTier,
        String credentialBinding,
        Integer maxConcurrent
) {
}
