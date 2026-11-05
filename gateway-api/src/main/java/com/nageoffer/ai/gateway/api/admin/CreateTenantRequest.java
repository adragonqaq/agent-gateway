package com.nageoffer.ai.gateway.api.admin;

public record CreateTenantRequest(
        String name,
        String modelTier,
        String credentialBinding,
        Integer maxConcurrent
) {
}
