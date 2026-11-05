package com.nageoffer.ai.gateway.tenant;

public record IssuedApiKey(String id, String rawKey, String prefix, String role) {
}
