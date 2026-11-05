package com.agent.gateway.runtime;

import com.agent.gateway.tenant.TenantIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class ConfirmGate {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration timeout;

    public ConfirmGate(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC(), Duration.ofSeconds(300));
    }

    ConfirmGate(JdbcTemplate jdbc, Clock clock, Duration timeout) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.timeout = timeout;
    }

    public String suspend(String tenantId, String taskId, String toolName, String argsJson) {
        String tid = TenantIds.require(tenantId);
        String id = "cfm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant expires = clock.instant().plus(timeout);
        jdbc.update("""
                INSERT INTO gw_confirm (id, tenant_id, task_id, tool_name, args_json, status, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id, tid, taskId, toolName, argsJson, ConfirmDecision.PENDING.name(),
                java.sql.Timestamp.from(expires));
        return id;
    }

    public PendingConfirmation confirm(String tenantId, String confirmId) {
        return decide(tenantId, confirmId, ConfirmDecision.CONFIRMED);
    }

    public PendingConfirmation deny(String tenantId, String confirmId) {
        return decide(tenantId, confirmId, ConfirmDecision.DENIED);
    }

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
            return new PendingConfirmation(pending.id(), pending.tenantId(), pending.taskId(),
                    pending.toolName(), pending.argsJson(), ConfirmDecision.EXPIRED, pending.expiresAt());
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
        return new PendingConfirmation(current.id(), current.tenantId(), current.taskId(),
                current.toolName(), current.argsJson(), decision, current.expiresAt());
    }

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
