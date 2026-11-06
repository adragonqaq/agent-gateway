package com.agent.gateway.observe;

import com.agent.gateway.tenant.TenantIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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
     * 记录一条用量事件（无用户）。
     *
     * @param tenantId 租户 ID
     * @param taskId 关联任务 ID
     * @param metric 指标名
     * @param amount 增量
     */
    public void record(String tenantId, String taskId, String metric, long amount) {
        record(tenantId, null, taskId, metric, amount);
    }

    /**
     * 记录一条用量事件。
     *
     * @param tenantId 租户 ID
     * @param userId 归属用户；可空
     * @param taskId 关联任务 ID
     * @param metric 指标名
     * @param amount 增量
     */
    public void record(String tenantId, String userId, String taskId, String metric, long amount) {
        String tid = TenantIds.require(tenantId);
        jdbc.update("""
                INSERT INTO gw_usage_event (id, tenant_id, user_id, task_id, metric, amount)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                "usg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                tid, blankToNull(userId), taskId, metric, amount);
    }

    /**
     * 汇总某租户某指标的用量总和。
     *
     * @param tenantId 租户 ID
     * @param metric 指标名
     * @return 合计；空则 0
     */
    public long sumForTenant(String tenantId, String metric) {
        Long n = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM gw_usage_event WHERE tenant_id = ? AND metric = ?",
                Long.class, TenantIds.require(tenantId), metric);
        return n == null ? 0L : n;
    }

    /**
     * 汇总某租户下某用户某指标。
     *
     * @param tenantId 租户
     * @param userId 用户
     * @param metric 指标
     * @return 合计
     */
    public long sumForUser(String tenantId, String userId, String metric) {
        if (userId == null || userId.isBlank()) {
            return 0L;
        }
        Long n = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(amount),0) FROM gw_usage_event
                WHERE tenant_id = ? AND user_id = ? AND metric = ?
                """,
                Long.class, TenantIds.require(tenantId), userId.trim(), metric);
        return n == null ? 0L : n;
    }

    /**
     * 按指标汇总本租户用量。
     *
     * @param tenantId 租户 ID
     * @return 每行含 metric、total
     */
    public List<Map<String, Object>> summarizeForTenant(String tenantId) {
        return summarize(tenantId, null);
    }

    /**
     * 按指标汇总；{@code userId} 非空时仅该用户。
     *
     * @param tenantId 租户
     * @param userId 用户过滤；null 全项目
     * @return metric / total 行
     */
    public List<Map<String, Object>> summarize(String tenantId, String userId) {
        String tid = TenantIds.require(tenantId);
        if (userId == null || userId.isBlank()) {
            return jdbc.queryForList("""
                    SELECT metric, COALESCE(SUM(amount),0) AS total
                    FROM gw_usage_event
                    WHERE tenant_id = ?
                    GROUP BY metric
                    ORDER BY metric
                    """, tid);
        }
        return jdbc.queryForList("""
                SELECT metric, COALESCE(SUM(amount),0) AS total
                FROM gw_usage_event
                WHERE tenant_id = ? AND user_id = ?
                GROUP BY metric
                ORDER BY metric
                """, tid, userId.trim());
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
        Map<String, Long> out = new LinkedHashMap<>();
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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
