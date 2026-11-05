package com.agent.gateway.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 基于 JDBC 的租户与 API Key 仓库实现。
 * 属于 tenant 模块；写路径强制 {@link TenantIds#require(String)}，鉴权查询联表要求 Key 与租户均为 ACTIVE。
 */
@Repository
public class JdbcTenantRepository implements TenantRepository {

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc Spring JDBC 模板，依赖数据源
     */
    public JdbcTenantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 插入租户行到 {@code gw_tenant}。
     *
     * @param tenant 待写入租户
     */
    @Override
    public void insertTenant(TenantRecord tenant) {
        // 写入前校验租户 ID，避免空 id 破坏隔离主键
        String id = TenantIds.require(tenant.id());
        jdbc.update("""
                INSERT INTO gw_tenant (id, name, status, model_tier, credential_binding, max_concurrent)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id, tenant.name(), tenant.status(), tenant.modelTier(),
                tenant.credentialBinding(), tenant.maxConcurrent());
    }

    /**
     * 插入 API Key 行到 {@code gw_api_key}（仅哈希）。
     *
     * @param apiKey 待写入 Key
     */
    @Override
    public void insertApiKey(ApiKeyRecord apiKey) {
        // Key 必须挂在合法租户下，防止孤儿 Key
        String tenantId = TenantIds.require(apiKey.tenantId());
        jdbc.update("""
                INSERT INTO gw_api_key (id, tenant_id, key_hash, key_prefix, role, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                apiKey.id(), tenantId, apiKey.keyHash(), apiKey.keyPrefix(),
                apiKey.role(), apiKey.status());
    }

    /**
     * 按主键读取租户。
     *
     * @param tenantId 租户 ID
     * @return 首行映射为 {@link TenantRecord}，无则 empty
     */
    @Override
    public Optional<TenantRecord> findTenant(String tenantId) {
        String id = TenantIds.require(tenantId);
        List<TenantRecord> rows = jdbc.query("""
                SELECT id, name, status, model_tier, credential_binding, max_concurrent
                FROM gw_tenant WHERE id = ?
                """,
                (rs, i) -> new TenantRecord(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getString("model_tier"),
                        rs.getString("credential_binding"),
                        rs.getInt("max_concurrent")),
                id);
        return rows.stream().findFirst();
    }

    /**
     * 按哈希查找 ACTIVE Key，且所属租户也必须 ACTIVE。
     *
     * @param keyHash Key 的 SHA-256 十六进制
     * @return 命中则返回 {@link ApiKeyRecord}
     */
    @Override
    public Optional<ApiKeyRecord> findActiveByKeyHash(String keyHash) {
        // 联表过滤停用 Key/租户，避免停用主体仍能通过鉴权进数据面
        List<ApiKeyRecord> rows = jdbc.query("""
                SELECT k.id, k.tenant_id, k.key_hash, k.key_prefix, k.role, k.status
                FROM gw_api_key k
                INNER JOIN gw_tenant t ON t.id = k.tenant_id
                WHERE k.key_hash = ?
                  AND k.status = 'ACTIVE'
                  AND t.status = 'ACTIVE'
                """,
                (rs, i) -> new ApiKeyRecord(
                        rs.getString("id"),
                        rs.getString("tenant_id"),
                        rs.getString("key_hash"),
                        rs.getString("key_prefix"),
                        rs.getString("role"),
                        rs.getString("status")),
                keyHash);
        return rows.stream().findFirst();
    }
}
