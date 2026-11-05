package com.agent.gateway.runtime;

import com.agent.gateway.tenant.TenantIds;
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
     * 生产环境构造：UTC 时钟，默认 300 秒超时。
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
    ConfirmGate(JdbcTemplate jdbc, Clock clock, Duration timeout) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.timeout = timeout;
    }

    /**
     * 挂起一次待确认的工具调用。
     *
     * @param tenantId 租户 ID
     * @param taskId 任务 ID
     * @param toolName 工具名
     * @param argsJson 参数 JSON
     * @return 新确认单 ID
     */
    public String suspend(String tenantId, String taskId, String toolName, String argsJson) {
        String tid = TenantIds.require(tenantId);
        String id = "cfm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant expires = clock.instant().plus(timeout);
        // 写入 PENDING，阻塞危险工具直至用户确认或超时
        jdbc.update("""
                INSERT INTO gw_confirm (id, tenant_id, task_id, tool_name, args_json, status, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id, tid, taskId, toolName, argsJson, ConfirmDecision.PENDING.name(),
                java.sql.Timestamp.from(expires));
        return id;
    }

    /**
     * 用户确认该工具调用。
     *
     * @param tenantId 租户 ID
     * @param confirmId 确认单 ID
     * @return 更新后的确认单
     */
    public PendingConfirmation confirm(String tenantId, String confirmId) {
        return decide(tenantId, confirmId, ConfirmDecision.CONFIRMED);
    }

    /**
     * 用户拒绝该工具调用。
     *
     * @param tenantId 租户 ID
     * @param confirmId 确认单 ID
     * @return 更新后的确认单
     */
    public PendingConfirmation deny(String tenantId, String confirmId) {
        return decide(tenantId, confirmId, ConfirmDecision.DENIED);
    }

    /**
     * 查询确认单；若仍为 PENDING 且已超时则标记 EXPIRED。
     *
     * @param tenantId 租户 ID
     * @param confirmId 确认单 ID
     * @return 当前确认单
     * @throws NotFoundException 记录不存在
     */
    public PendingConfirmation get(String tenantId, String confirmId) {
        String tid = TenantIds.require(tenantId);
        List<PendingConfirmation> rows = load(tid, confirmId);
        if (rows.isEmpty()) {
            throw new NotFoundException("confirmation not found");
        }
        PendingConfirmation pending = rows.get(0);
        if (pending.status() == ConfirmDecision.PENDING && clock.instant().isAfter(pending.expiresAt())) {
            // 惰性过期：读取时才落库 EXPIRED，避免额外定时任务
            jdbc.update("UPDATE gw_confirm SET status = ? WHERE id = ? AND tenant_id = ?",
                    ConfirmDecision.EXPIRED.name(), confirmId, tid);
            return new PendingConfirmation(pending.id(), pending.tenantId(), pending.taskId(),
                    pending.toolName(), pending.argsJson(), ConfirmDecision.EXPIRED, pending.expiresAt());
        }
        return pending;
    }

    /**
     * 在 PENDING 状态下落库用户决策；已终态则原样返回。
     *
     * @param tenantId 租户 ID
     * @param confirmId 确认单 ID
     * @param decision CONFIRMED 或 DENIED
     * @return 决策后的确认单
     */
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
        return new PendingConfirmation(current.id(), current.tenantId(), current.taskId(),
                current.toolName(), current.argsJson(), decision, current.expiresAt());
    }

    /**
     * 按租户+确认单 ID 加载行。
     *
     * @param tenantId 已校验租户 ID
     * @param confirmId 确认单 ID
     * @return 0 或 1 条记录
     */
    private List<PendingConfirmation> load(String tenantId, String confirmId) {
        return jdbc.query("""
                SELECT id, tenant_id, task_id, tool_name, args_json, status, expires_at
                FROM gw_confirm WHERE tenant_id = ? AND id = ?
                """,
                (rs, i) -> new PendingConfirmation(
                        rs.getString("id"),
                        rs.getString("tenant_id"),
                        rs.getString("task_id"),
                        rs.getString("tool_name"),
                        rs.getString("args_json"),
                        ConfirmDecision.valueOf(rs.getString("status")),
                        rs.getTimestamp("expires_at").toInstant()),
                tenantId, confirmId);
    }
}
