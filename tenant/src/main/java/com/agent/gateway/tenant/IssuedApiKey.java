package com.agent.gateway.tenant;

public record IssuedApiKey(String id, String rawKey, String prefix, String role) {
}
