package com.agent.gateway.observe;

import com.agent.gateway.tenant.TenantIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 审计事件写入与按租户统计服务。
 * 属于 observe 模块；每条事件强制绑定 tenant_id，供合规追溯与管理面计数。
 */
@Service
public class AuditService {

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc Spring JDBC 模板
     */
    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 记录一条租户审计事件。
     *
     * @param tenantId 租户 ID
     * @param eventType 事件类型
     * @param refId 关联业务 ID（任务/会话等）
     * @param detailJson 详情 JSON
     */
    public void record(String tenantId, String eventType, String refId, String detailJson) {
        // 写入前校验租户，保证审计行可按租户隔离查询
        String tid = TenantIds.require(tenantId);
        jdbc.update("""
                INSERT INTO gw_audit_event (id, tenant_id, event_type, ref_id, detail_json)
                VALUES (?, ?, ?, ?, ?)
                """,
                "aud_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                tid, eventType, refId, detailJson);
    }

    /**
     * 统计某租户审计事件条数。
     *
     * @param tenantId 租户 ID
     * @return 事件数；查询空结果按 0
     */
    public int countForTenant(String tenantId) {
        // COUNT 条件带 tenant_id，避免跨租户汇总
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM gw_audit_event WHERE tenant_id = ?",
                Integer.class, TenantIds.require(tenantId));
        return n == null ? 0 : n;
    }
}
