package com.agent.gateway.observe;

import com.agent.gateway.tenant.TenantIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UsageService {

    private final JdbcTemplate jdbc;

    public UsageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String tenantId, String taskId, String metric, long amount) {
        String tid = TenantIds.require(tenantId);
        jdbc.update("""
                INSERT INTO gw_usage_event (id, tenant_id, task_id, metric, amount)
                VALUES (?, ?, ?, ?, ?)
                """,
                "usg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                tid, taskId, metric, amount);
    }

    public long sumForTenant(String tenantId, String metric) {
        Long n = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM gw_usage_event WHERE tenant_id = ? AND metric = ?",
                Long.class, TenantIds.require(tenantId), metric);
        return n == null ? 0L : n;
    }
}
