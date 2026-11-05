package com.agent.gateway.config;

import com.agent.gateway.tenant.ApiKeyHasher;
import com.agent.gateway.tenant.ApiKeyRecord;
import com.agent.gateway.tenant.TenantAdminService;
import com.agent.gateway.tenant.TenantRecord;
import com.agent.gateway.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时种子平台租户与 ADMIN API Key。
 * <p>所属模块：bootstrap。仅当配置了 {@code gateway.bootstrap.admin-api-key} 且库中尚无该 Key 时写入，
 * 便于本地/首次部署立刻调用管理面。
 */
@Component
@DependsOn("schemaInitializer")
@Order(Ordered.LOWEST_PRECEDENCE)
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final TenantRepository tenantRepository;
    private final TenantAdminService tenantAdminService;
    private final String adminApiKey;
    private final String adminTenantName;

    /**
     * @param tenantRepository 租户仓库，用于查重 Key
     * @param tenantAdminService 管理服务（创建租户）
     * @param adminApiKey 明文 ADMIN Key；为空则跳过种子
     * @param adminTenantName 平台租户名称
     */
    public AdminBootstrap(
            TenantRepository tenantRepository,
            TenantAdminService tenantAdminService,
            @Value("${gateway.bootstrap.admin-api-key:}") String adminApiKey,
            @Value("${gateway.bootstrap.admin-tenant-name:platform}") String adminTenantName) {
        this.tenantRepository = tenantRepository;
        this.tenantAdminService = tenantAdminService;
        this.adminApiKey = adminApiKey;
        this.adminTenantName = adminTenantName;
    }

    /**
     * 应用就绪后按需写入平台 ADMIN Key。
     *
     * @param args 启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        if (adminApiKey == null || adminApiKey.isBlank()) {
            // 未配置则不落库，避免生产环境意外注入弱 Key
            log.info("gateway.bootstrap.admin-api-key 未配置，跳过 ADMIN 种子");
            return;
        }
        String hash = ApiKeyHasher.sha256Hex(adminApiKey.trim());
        if (tenantRepository.findActiveByKeyHash(hash).isPresent()) {
            // 已存在则幂等跳过，支持多次重启
            log.info("ADMIN API Key 已存在，跳过种子");
            return;
        }
        // 先建平台租户，再写入指定明文对应的哈希 Key
        TenantRecord tenant = tenantAdminService.createTenant(adminTenantName, "standard", "platform", 10);
        String keyId = "key_admin_bootstrap";
        String prefix = adminApiKey.trim().substring(0, Math.min(12, adminApiKey.trim().length()));
        tenantRepository.insertApiKey(new ApiKeyRecord(
                keyId, tenant.id(), hash, prefix, "ADMIN", "ACTIVE"));
        log.warn("已种子 ADMIN API Key（明文仅来自环境变量，请妥善保管），tenantId={}", tenant.id());
    }
}
