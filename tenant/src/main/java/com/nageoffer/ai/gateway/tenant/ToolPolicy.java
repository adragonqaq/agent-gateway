package com.nageoffer.ai.gateway.tenant;

public record ToolPolicy(String toolName, String effect, boolean requireConfirm) {

    public boolean denied() {
        return "DENY".equalsIgnoreCase(effect);
    }
}
