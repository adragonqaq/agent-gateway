package com.agent.gateway.tenant;

import java.util.Optional;

/**
 * 租户与 API Key 持久化端口。
 * 属于 tenant 模块；实现方必须按租户隔离读写，鉴权查询只认哈希与 ACTIVE 状态。
 */
public interface TenantRepository {

    /**
     * 插入租户主数据。
     *
     * @param tenant 待写入租户记录
     */
    void insertTenant(TenantRecord tenant);

    /**
     * 插入 API Key 元数据（含哈希，无明文）。
     *
     * @param apiKey 待写入 Key 记录
     */
    void insertApiKey(ApiKeyRecord apiKey);

    /**
     * 按 ID 查询租户。
     *
     * @param tenantId 租户 ID
     * @return 存在则返回记录
     */
    Optional<TenantRecord> findTenant(String tenantId);

    /**
     * 按 Key 哈希查找仍 ACTIVE 且所属租户 ACTIVE 的 Key。
     *
     * @param keyHash SHA-256 十六进制摘要
     * @return 命中则返回 Key 记录
     */
    Optional<ApiKeyRecord> findActiveByKeyHash(String keyHash);
}
