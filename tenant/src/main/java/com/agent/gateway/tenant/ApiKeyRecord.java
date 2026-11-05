package com.agent.gateway.tenant;

public record ApiKeyRecord(
        String id,
        String tenantId,
        String keyHash,
        String keyPrefix,
        String role,
        String status
) {
}
