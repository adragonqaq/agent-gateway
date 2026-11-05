package com.agent.gateway.tenant;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 租户管理面服务：创建租户与签发 API Key。
 * 属于 tenant 模块；签发时只把哈希入库，明文仅通过 {@link IssuedApiKey} 一次性返回。
 */
@Service
public class TenantAdminService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantRepository tenantRepository;

    /**
     * @param tenantRepository 租户/Key 持久化端口
     */
    public TenantAdminService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * 创建 ACTIVE 租户并写入仓库。
     *
     * @param name 展示名
     * @param modelTier 模型档位；空则 standard
     * @param credentialBinding 凭证绑定；空则 platform
     * @param maxConcurrent 并发上限；≤0 则默认 5
     * @return 已持久化的租户记录
     */
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

    /**
     * 为已存在租户签发新 API Key。
     *
     * @param tenantId 目标租户
     * @param role Key 角色；空则 RUNTIME
     * @return 含明文的一次性回执
     * @throws UnauthorizedException 租户不存在
     * @throws MissingTenantException tenantId 空白
     */
    public IssuedApiKey issueApiKey(String tenantId, String role) {
        // 先校验租户 ID 形态，再确认租户真实存在
        TenantIds.require(tenantId);
        tenantRepository.findTenant(tenantId)
                .orElseThrow(() -> new UnauthorizedException("Tenant not found"));
        String id = "key_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        byte[] bytes = new byte[24];
        // 用安全随机生成明文 Key 材料，降低可预测性
        RANDOM.nextBytes(bytes);
        String raw = "sk_" + HexFormat.of().formatHex(bytes);
        String prefix = raw.substring(0, Math.min(12, raw.length()));
        String resolvedRole = role == null || role.isBlank() ? "RUNTIME" : role;
        // 仅存哈希：明文只经 IssuedApiKey 返回一次
        tenantRepository.insertApiKey(new ApiKeyRecord(
                id, tenantId, ApiKeyHasher.sha256Hex(raw), prefix, resolvedRole, "ACTIVE"));
        return new IssuedApiKey(id, raw, prefix, resolvedRole);
    }
}
