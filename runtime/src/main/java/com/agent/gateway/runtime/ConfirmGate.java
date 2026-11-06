package com.agent.gateway.runtime;

import com.agent.gateway.tenant.TenantIds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 工具调用人工确认闸门。
 * <p>所属模块：runtime。将危险工具调用挂起写入 {@code gw_confirm}，供用户确认/拒绝后继续。
 */
@Component
public class ConfirmGate {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration timeout;

    /**
     * 生产环境构造：UTC 时钟，超时取自 {@code gateway.confirm.timeout-seconds}。
     *
     * @param jdbc JDBC 模板
     * @param properties 确认闸门配置
     */
    @Autowired
    public ConfirmGate(JdbcTemplate jdbc, com.agent.gateway.runtime.config.GatewayConfirmProperties properties) {
        this(jdbc, Clock.systemUTC(), Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds())));
    }

    /**
     * 仅 JDBC 的兼容构造（测试 / 无 Spring 配置时默认 300 秒）。
     *
     * @param jdbc JDBC 模板
     */
    public ConfirmGate(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC(), Duration.ofSeconds(300));
    }

    /**
     * 可注入时钟与超时的构造，便于单测。
     *
     * @param jdbc JDBC 模板
     * @param clock 时钟
     * @param timeout 确认超时时长
     */
    public ConfirmGate(JdbcTemplate jdbc, Clock clock, Duration timeout) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.timeout = timeout;
    }

    /**
     * 挂起一次待确认的工具调用（无 trace）。
     */
    public String suspend(String tenantId, String taskId, String conversationId, String toolName, String argsJson) {
        return suspend(tenantId, taskId, conversationId, null, toolName, argsJson);
    }

    /**
     * 挂起一次待确认的工具调用。
     *
     * @param tenantId 租户 ID
     * @param taskId 任务 ID
     * @param conversationId 会话 ID，供后续续跑定位 Agent 状态
     * @param traceId 全链路 ID；可空
     * @param toolName 工具名
     * @param argsJson 参数 JSON
     * @return 新确认单 ID
     */
    public String suspend(
            String tenantId,
            String taskId,
            String conversationId,
            String traceId,
            String toolName,
            String argsJson) {
        String tid = TenantIds.require(tenantId);
        String id = "cfm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant expires = clock.instant().plus(timeout);
        jdbc.update("""
                INSERT INTO gw_confirm
                (id, tenant_id, task_id, conversation_id, trace_id, tool_name, args_json, status, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, tid, taskId, conversationId, traceId, toolName, argsJson, ConfirmDecision.PENDING.name(),
                java.sql.Timestamp.from(expires));
        return id;
    }

    /**
     * 用户确认该工具调用。
     */
    public PendingConfirmation confirm(String tenantId, String confirmId) {
        return decide(tenantId, confirmId, ConfirmDecision.CONFIRMED);
    }

    /**
     * 用户拒绝该工具调用。
     */
    public PendingConfirmation deny(String tenantId, String confirmId) {
        return decide(tenantId, confirmId, ConfirmDecision.DENIED);
    }

    /**
     * 将同一任务下仍为 PENDING 的确认单统一落成同一决策。
     */
    public List<PendingConfirmation> decideAllPendingForTask(String tenantId, String taskId, ConfirmDecision decision) {
        String tid = TenantIds.require(tenantId);
        List<PendingConfirmation> pending = listByTask(tid, taskId).stream()
                .filter(p -> p.status() == ConfirmDecision.PENDING)
                .toList();
        for (PendingConfirmation item : pending) {
            decide(tid, item.id(), decision);
        }
        return listByTask(tid, taskId);
    }

    /**
     * 按任务列出该租户下的确认单。
     */
    public List<PendingConfirmation> listByTask(String tenantId, String taskId) {
        String tid = TenantIds.require(tenantId);
        return jdbc.query("""
                SELECT id, tenant_id, task_id, conversation_id, trace_id, tool_name, args_json, status, expires_at
                FROM gw_confirm WHERE tenant_id = ? AND task_id = ?
                ORDER BY created_at
                """,
                (rs, i) -> mapRow(rs),
                tid, taskId);
    }

    /**
     * 批量将已到期的 PENDING 确认单标记为 EXPIRED。
     */
    public List<PendingConfirmation> expireDue(int limit) {
        int n = Math.max(1, Math.min(limit, 500));
        Instant now = clock.instant();
        List<PendingConfirmation> due = jdbc.query("""
                SELECT id, tenant_id, task_id, conversation_id, trace_id, tool_name, args_json, status, expires_at
                FROM gw_confirm
                WHERE status = ? AND expires_at < ?
                ORDER BY expires_at
                LIMIT ?
                """,
                (rs, i) -> mapRow(rs),
                ConfirmDecision.PENDING.name(),
                java.sql.Timestamp.from(now),
                n);
        List<PendingConfirmation> expired = new java.util.ArrayList<>();
        for (PendingConfirmation row : due) {
            int updated = jdbc.update(
                    "UPDATE gw_confirm SET status = ? WHERE id = ? AND tenant_id = ? AND status = ?",
                    ConfirmDecision.EXPIRED.name(), row.id(), row.tenantId(), ConfirmDecision.PENDING.name());
            if (updated > 0) {
                expired.add(withStatus(row, ConfirmDecision.EXPIRED));
            }
        }
        return expired;
    }

    /**
     * 查询确认单；若仍为 PENDING 且已超时则标记 EXPIRED。
     */
    public PendingConfirmation get(String tenantId, String confirmId) {
        String tid = TenantIds.require(tenantId);
        List<PendingConfirmation> rows = load(tid, confirmId);
        if (rows.isEmpty()) {
            throw new NotFoundException("confirmation not found");
        }
        PendingConfirmation pending = rows.get(0);
        if (pending.status() == ConfirmDecision.PENDING && clock.instant().isAfter(pending.expiresAt())) {
            jdbc.update("UPDATE gw_confirm SET status = ? WHERE id = ? AND tenant_id = ?",
                    ConfirmDecision.EXPIRED.name(), confirmId, tid);
            return withStatus(pending, ConfirmDecision.EXPIRED);
        }
        return pending;
    }

    private PendingConfirmation decide(String tenantId, String confirmId, ConfirmDecision decision) {
        PendingConfirmation current = get(tenantId, confirmId);
        if (current.status() == ConfirmDecision.EXPIRED) {
            return current;
        }
        if (current.status() != ConfirmDecision.PENDING) {
            return current;
        }
        jdbc.update("UPDATE gw_confirm SET status = ? WHERE id = ? AND tenant_id = ?",
                decision.name(), confirmId, TenantIds.require(tenantId));
        return withStatus(current, decision);
    }

    private List<PendingConfirmation> load(String tenantId, String confirmId) {
        return jdbc.query("""
                SELECT id, tenant_id, task_id, conversation_id, trace_id, tool_name, args_json, status, expires_at
                FROM gw_confirm WHERE tenant_id = ? AND id = ?
                """,
                (rs, i) -> mapRow(rs),
                tenantId, confirmId);
    }

    private static PendingConfirmation mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PendingConfirmation(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("task_id"),
                rs.getString("conversation_id"),
                rs.getString("trace_id"),
                rs.getString("tool_name"),
                rs.getString("args_json"),
                ConfirmDecision.valueOf(rs.getString("status")),
                rs.getTimestamp("expires_at").toInstant());
    }

    private static PendingConfirmation withStatus(PendingConfirmation src, ConfirmDecision status) {
        return new PendingConfirmation(
                src.id(), src.tenantId(), src.taskId(), src.conversationId(), src.traceId(),
                src.toolName(), src.argsJson(), status, src.expiresAt());
    }
}
