package com.nageoffer.ai.gateway.tools;

public record CatalogTool(
        String name,
        String description,
        String serverId,
        boolean available,
        boolean requireConfirm
) {
}
