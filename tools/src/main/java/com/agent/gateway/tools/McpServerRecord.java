package com.agent.gateway.tools;

public record McpServerRecord(
        String id,
        String tenantId,
        String name,
        String transportUrl,
        String headersCipher,
        String status
) {
}
