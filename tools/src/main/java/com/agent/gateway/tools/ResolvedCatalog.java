package com.agent.gateway.tools;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

public record ResolvedCatalog(String tenantId, List<CatalogTool> tools) {

    public String fingerprint() {
        String raw = tenantId + "|" + tools.stream()
                .map(t -> t.name() + ":" + t.available() + ":" + t.requireConfirm())
                .sorted()
                .collect(Collectors.joining(","));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
