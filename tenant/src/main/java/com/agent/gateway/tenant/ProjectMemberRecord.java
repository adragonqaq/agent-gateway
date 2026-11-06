package com.agent.gateway.tenant;

/**
 * 项目成员行。
 */
public record ProjectMemberRecord(
        String tenantId,
        String userId,
        String role
) {
}
