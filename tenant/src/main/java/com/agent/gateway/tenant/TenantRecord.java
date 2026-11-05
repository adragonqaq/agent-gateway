package com.agent.gateway.tenant;

public record TenantRecord(
        String id,
        String name,
        String status,
        String modelTier,
        String credentialBinding,
        int maxConcurrent
) {
}
