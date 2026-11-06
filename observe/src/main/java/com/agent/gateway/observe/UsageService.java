package com.agent.gateway.observe;

import com.agent.gateway.tenant.TenantIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用量事件写入与按租户指标汇总服务。
 * 属于 observe 模块；计量行绑定 tenant_id + metric，支撑计费与配额观测。
 */
@Service
public class UsageService {

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc Spring JDBC 模板
     */
    public UsageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 记录一条用量事件。
     *
     * @param tenantId 租户 ID
     * @param taskId 关联任务 ID
     * @param metric 指标名
     * @param amount 增量
     */
    public void record(String tenantId, String taskId, String metric, long amount) {
        // 用量必须挂租户，否则无法做租户级计量隔离
        String tid = TenantIds.require(tenantId);
        jdbc.update("""
                INSERT INTO gw_usage_event (id, tenant_id, task_id, metric, amount)
                VALUES (?, ?, ?, ?, ?)
                """,
                "usg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                tid, taskId, metric, amount);
    }

    /**
     * 汇总某租户某指标的用量总和。
     *
     * @param tenantId 租户 ID
     * @param metric 指标名
     * @return 合计；空则 0
     */
    public long sumForTenant(String tenantId, String metric) {
        // 按租户 + 指标过滤，防止跨租户计量串账
        Long n = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM gw_usage_event WHERE tenant_id = ? AND metric = ?",
                Long.class, TenantIds.require(tenantId), metric);
        return n == null ? 0L : n;
    }

    /**
     * 按指标汇总本租户用量。
     *
     * @param tenantId 租户 ID
     * @return 每行含 metric、total；仅本租户
     */
    public List<Map<String, Object>> summarizeForTenant(String tenantId) {
        String tid = TenantIds.require(tenantId);
        return jdbc.queryForList("""
                SELECT metric, COALESCE(SUM(amount),0) AS total
                FROM gw_usage_event
                WHERE tenant_id = ?
                GROUP BY metric
                ORDER BY metric
                """, tid);
    }

    /**
     * 按任务汇总用量（用于对话台回填本轮 token）。
     *
     * @param tenantId 租户
     * @param taskId 任务 ID
     * @return metric → total；无数据时为空 map
     */
    public Map<String, Long> summarizeForTask(String tenantId, String taskId) {
        String tid = TenantIds.require(tenantId);
        if (taskId == null || taskId.isBlank()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT metric, COALESCE(SUM(amount),0) AS total
                FROM gw_usage_event
                WHERE tenant_id = ? AND task_id = ?
                GROUP BY metric
                """, tid, taskId);
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object metric = row.get("metric");
            Object total = row.get("total");
            if (metric == null) {
                continue;
            }
            out.put(String.valueOf(metric), total == null ? 0L : ((Number) total).longValue());
        }
        return out;
    }
}
