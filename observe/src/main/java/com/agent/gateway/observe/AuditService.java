package com.agent.gateway.observe;

import com.agent.gateway.tenant.TenantIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String tenantId, String eventType, String refId, String detailJson) {
        String tid = TenantIds.require(tenantId);
        jdbc.update("""
                INSERT INTO gw_audit_event (id, tenant_id, event_type, ref_id, detail_json)
                VALUES (?, ?, ?, ?, ?)
                """,
                "aud_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                tid, eventType, refId, detailJson);
    }

    public int countForTenant(String tenantId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM gw_audit_event WHERE tenant_id = ?",
                Integer.class, TenantIds.require(tenantId));
        return n == null ? 0 : n;
    }
}
