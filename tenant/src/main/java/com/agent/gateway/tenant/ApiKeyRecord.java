package com.agent.gateway.tenant;

/**
 * API Key 持久化记录（仅含哈希与元数据，不含明文）。
 * 属于 tenant 模块。
 * <ul>
 *   <li>{@code id} — Key 主键（如 key_xxx）</li>
 *   <li>{@code tenantId} — 所属租户，隔离边界</li>
 *   <li>{@code keyHash} — 明文 Key 的 SHA-256 十六进制</li>
 *   <li>{@code keyPrefix} — 明文前缀，便于管理面展示/排查</li>
 *   <li>{@code role} — 角色（如 RUNTIME）</li>
 *   <li>{@code status} — ACTIVE / 停用等</li>
 * </ul>
 */
public record ApiKeyRecord(
        String id,
        String tenantId,
        String keyHash,
        String keyPrefix,
        String role,
        String status
) {
}
