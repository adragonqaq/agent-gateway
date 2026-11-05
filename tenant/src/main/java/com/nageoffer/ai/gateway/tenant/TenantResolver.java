package com.nageoffer.ai.gateway.tenant;

import org.springframework.stereotype.Component;

@Component
public class TenantResolver {

    private final TenantRepository tenantRepository;

    public TenantResolver(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public TenantContext resolve(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new UnauthorizedException("API key is required");
        }
        String hash = ApiKeyHasher.sha256Hex(rawApiKey.trim());
        ApiKeyRecord key = tenantRepository.findActiveByKeyHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid API key"));
        TenantRecord tenant = tenantRepository.findTenant(key.tenantId())
                .orElseThrow(() -> new UnauthorizedException("Invalid API key"));
        return new TenantContext(
                tenant.id(), key.id(), key.role(), tenant.modelTier(), tenant.credentialBinding());
    }
}
