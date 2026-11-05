package com.agent.gateway.runtime;

import java.time.Instant;

public record PendingConfirmation(
        String id,
        String tenantId,
        String taskId,
        String toolName,
        String argsJson,
        ConfirmDecision status,
        Instant expiresAt
) {
}
