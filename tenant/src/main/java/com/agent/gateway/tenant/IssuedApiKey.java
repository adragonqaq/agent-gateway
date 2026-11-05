package com.agent.gateway.tenant;

/**
 * 新签发 API Key 的一次性回执（含明文，仅创建时返回给调用方）。
 * 属于 tenant 模块；之后仅存哈希，故 rawKey 不得再入库存日志。
 * <ul>
 *   <li>{@code id} — Key 主键</li>
 *   <li>{@code rawKey} — 明文 Key（仅此一次可见）</li>
 *   <li>{@code prefix} — 展示用前缀</li>
 *   <li>{@code role} — 绑定角色</li>
 * </ul>
 */
public record IssuedApiKey(String id, String rawKey, String prefix, String role) {
}
