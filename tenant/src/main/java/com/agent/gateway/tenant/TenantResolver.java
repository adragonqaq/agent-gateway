package com.agent.gateway.tenant;

import org.springframework.stereotype.Component;

/**
 * 按租户解析 API Key，产出请求级 {@link TenantContext}。
 * 属于 tenant 模块；无效 Key 统一抛 {@link UnauthorizedException}，避免泄露租户是否存在。
 */
@Component
public class TenantResolver {

    private final TenantRepository tenantRepository;

    /**
     * @param tenantRepository 用于按哈希查 Key 与加载租户
     */
    public TenantResolver(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * 将原始 API Key 解析为租户上下文。
     *
     * @param rawApiKey 请求头中的明文 Key
     * @return 绑定了 tenantId / role / 模型档位的上下文
     * @throws UnauthorizedException Key 为空、哈希未命中或租户不可用
     */
    public TenantContext resolve(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new UnauthorizedException("API key is required");
        }
        // 只存/查哈希，避免明文 Key 进入查询条件或日志
        String hash = ApiKeyHasher.sha256Hex(rawApiKey.trim());
        // 联表要求 Key 与租户 ACTIVE，防止停用主体仍能进数据面
        ApiKeyRecord key = tenantRepository.findActiveByKeyHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid API key"));
        // 再取租户档位等配置；缺失时仍报 Invalid，避免区分「Key 无效」与「租户没了」
        TenantRecord tenant = tenantRepository.findTenant(key.tenantId())
                .orElseThrow(() -> new UnauthorizedException("Invalid API key"));
        return new TenantContext(
                tenant.id(), key.id(), key.role(), tenant.modelTier(), tenant.credentialBinding());
    }
}
