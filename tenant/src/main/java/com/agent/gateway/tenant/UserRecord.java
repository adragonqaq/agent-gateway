package com.agent.gateway.tenant;

/**
 * 登录用户行。
 */
public record UserRecord(
        String id,
        String email,
        String passwordHash,
        String status
) {
}
