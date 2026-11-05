package com.agent.gateway.api.admin;

/**
 * 为租户签发 API Key 的请求体。
 *
 * @param role Key 角色（如 ADMIN / RUNTIME）；为空时由服务端默认 RUNTIME
 */
public record IssueApiKeyRequest(String role) {
}
