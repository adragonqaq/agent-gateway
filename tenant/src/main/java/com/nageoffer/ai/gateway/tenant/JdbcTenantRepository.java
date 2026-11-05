package com.nageoffer.ai.gateway.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcTenantRepository implements TenantRepository {

    private final JdbcTemplate jdbc;

    public JdbcTenantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertTenant(TenantRecord tenant) {
        String id = TenantIds.require(tenant.id());
        jdbc.update("""
                INSERT INTO gw_tenant (id, name, status, model_tier, credential_binding, max_concurrent)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id, tenant.name(), tenant.status(), tenant.modelTier(),
                tenant.credentialBinding(), tenant.maxConcurrent());
    }

    @Override
    public void insertApiKey(ApiKeyRecord apiKey) {
        String tenantId = TenantIds.require(apiKey.tenantId());
        jdbc.update("""
                INSERT INTO gw_api_key (id, tenant_id, key_hash, key_prefix, role, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                apiKey.id(), tenantId, apiKey.keyHash(), apiKey.keyPrefix(),
                apiKey.role(), apiKey.status());
    }

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

    @Override
    public Optional<ApiKeyRecord> findActiveByKeyHash(String keyHash) {
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
