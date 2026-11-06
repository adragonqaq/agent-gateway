package com.agent.gateway.observe;

import com.agent.gateway.tenant.TenantIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * Trace Span 写入服务。
 * <p>所属模块：observe。start 插入行，end 用 Java 计算 duration（Postgres/H2 一致）。
 */
@Service
public class SpanRecorder {

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc JDBC 模板
     */
    public SpanRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 开启一个 Span 并落库。
     *
     * @param tenantId 租户
     * @param traceId 链路
     * @param parentSpanId 父节点；根传 null
     * @param conversationId 会话
     * @param taskId 任务
     * @param name 名称
     * @param kind 类型
     * @param status 初始状态
     * @param attrsJson 附加属性
     * @return 新 Span ID
     */
    public String start(
            String tenantId,
            String traceId,
            String parentSpanId,
            String conversationId,
            String taskId,
            String name,
            SpanKind kind,
            SpanStatus status,
            String attrsJson) {
        String tid = TenantIds.require(tenantId);
        String id = TraceIds.newSpanId();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO gw_trace_span
                (id, tenant_id, trace_id, parent_span_id, conversation_id, task_id,
                 name, kind, status, started_at, attrs_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, tid, traceId, parentSpanId, conversationId, taskId,
                name, kind.name(), status.name(), Timestamp.from(now), attrsJson);
        return id;
    }

    /**
     * 结束 Span：写 ended_at / duration_ms / 终态。
     *
     * @param tenantId 租户
     * @param spanId Span ID
     * @param status 终态
     * @param attrsJson 可选覆盖属性；null 则保留原值
     */
    public void end(String tenantId, String spanId, SpanStatus status, String attrsJson) {
        String tid = TenantIds.require(tenantId);
        Instant now = Instant.now();
        Optional<Instant> started = jdbc.query("""
                SELECT started_at FROM gw_trace_span WHERE id = ? AND tenant_id = ?
                """, rs -> rs.next() ? Optional.of(rs.getTimestamp("started_at").toInstant()) : Optional.empty(),
                spanId, tid);
        long durationMs = started.map(s -> Math.max(0L, now.toEpochMilli() - s.toEpochMilli())).orElse(0L);
        if (attrsJson == null) {
            jdbc.update("""
                    UPDATE gw_trace_span
                    SET status = ?, ended_at = ?, duration_ms = ?
                    WHERE id = ? AND tenant_id = ?
                    """,
                    status.name(), Timestamp.from(now), durationMs, spanId, tid);
        } else {
            jdbc.update("""
                    UPDATE gw_trace_span
                    SET status = ?, ended_at = ?, duration_ms = ?, attrs_json = ?
                    WHERE id = ? AND tenant_id = ?
                    """,
                    status.name(), Timestamp.from(now), durationMs, attrsJson, spanId, tid);
        }
    }

    /** 成功结束。 */
    public void ok(String tenantId, String spanId) {
        end(tenantId, spanId, SpanStatus.OK, null);
    }

    /** 失败结束。 */
    public void fail(String tenantId, String spanId, String attrsJson) {
        end(tenantId, spanId, SpanStatus.ERROR, attrsJson);
    }

    /** 取消结束。 */
    public void cancel(String tenantId, String spanId) {
        end(tenantId, spanId, SpanStatus.CANCELLED, null);
    }
}
