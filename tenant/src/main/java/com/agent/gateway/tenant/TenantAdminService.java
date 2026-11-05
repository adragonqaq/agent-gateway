package com.agent.gateway.tenant;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class TenantAdminService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantRepository tenantRepository;

    public TenantAdminService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public TenantRecord createTenant(String name, String modelTier, String credentialBinding, int maxConcurrent) {
        String id = "ten_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        TenantRecord tenant = new TenantRecord(
                id,
                name,
                "ACTIVE",
                modelTier == null || modelTier.isBlank() ? "standard" : modelTier,
                credentialBinding == null || credentialBinding.isBlank() ? "platform" : credentialBinding,
                maxConcurrent <= 0 ? 5 : maxConcurrent);
        tenantRepository.insertTenant(tenant);
        return tenant;
    }

    public IssuedApiKey issueApiKey(String tenantId, String role) {
        TenantIds.require(tenantId);
        tenantRepository.findTenant(tenantId)
                .orElseThrow(() -> new UnauthorizedException("Tenant not found"));
        String id = "key_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String raw = "sk_" + HexFormat.of().formatHex(bytes);
        String prefix = raw.substring(0, Math.min(12, raw.length()));
        String resolvedRole = role == null || role.isBlank() ? "RUNTIME" : role;
        tenantRepository.insertApiKey(new ApiKeyRecord(
                id, tenantId, ApiKeyHasher.sha256Hex(raw), prefix, resolvedRole, "ACTIVE"));
        return new IssuedApiKey(id, raw, prefix, resolvedRole);
    }
}
