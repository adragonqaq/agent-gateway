package com.nageoffer.ai.gateway.tools;

public record McpServerRecord(
        String id,
        String tenantId,
        String name,
        String transportUrl,
        String headersCipher,
        String status
) {
}
