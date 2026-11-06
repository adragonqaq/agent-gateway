package com.agent.gateway.observe;

import com.agent.gateway.tenant.TenantIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trace 查询与树组装。
 * <p>所属模块：observe。强制按 tenant_id 隔离。
 */
@Service
public class TraceQueryService {

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc JDBC 模板
     */
    public TraceQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 按 traceId 加载本租户全部 span（时间序）。
     *
     * @param tenantId 租户
     * @param traceId 链路 ID
     * @return span 列表
     */
    public List<SpanRecord> listSpans(String tenantId, String traceId) {
        String tid = TenantIds.require(tenantId);
        return jdbc.query("""
                SELECT id, tenant_id, trace_id, parent_span_id, conversation_id, task_id,
                       name, kind, status, started_at, ended_at, duration_ms, attrs_json
                FROM gw_trace_span
                WHERE tenant_id = ? AND trace_id = ?
                ORDER BY started_at ASC, id ASC
                """,
                (rs, i) -> mapRow(rs),
                tid, traceId);
    }

    /**
     * 组装森林：无 parent 的为根；找不到父的也当根挂上，避免丢节点。
     *
     * @param spans 扁平列表
     * @return 根节点列表
     */
    public List<TraceTreeNode> buildTree(List<SpanRecord> spans) {
        Map<String, TraceTreeNode> nodes = new LinkedHashMap<>();
        for (SpanRecord span : spans) {
            nodes.put(span.id(), new TraceTreeNode(span));
        }
        List<TraceTreeNode> roots = new ArrayList<>();
        for (TraceTreeNode node : nodes.values()) {
            String parentId = node.span().parentSpanId();
            if (parentId == null || parentId.isBlank() || !nodes.containsKey(parentId)) {
                roots.add(node);
            } else {
                nodes.get(parentId).children().add(node);
            }
        }
        return roots;
    }

    /**
     * 完整 trace 视图：扁平 spans + 树。
     *
     * @param tenantId 租户
     * @param traceId 链路
     * @return map：traceId / spans / tree
     */
    public Map<String, Object> getTrace(String tenantId, String traceId) {
        List<SpanRecord> spans = listSpans(tenantId, traceId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("traceId", traceId);
        out.put("spans", spans);
        out.put("tree", buildTree(spans));
        return out;
    }

    /**
     * 按会话列出最近 trace 摘要。
     *
     * @param tenantId 租户
     * @param conversationId 会话
     * @param limit 条数上限
     * @return 摘要列表
     */
    public List<Map<String, Object>> listByConversation(String tenantId, String conversationId, int limit) {
        String tid = TenantIds.require(tenantId);
        int n = Math.max(1, Math.min(limit, 50));
        List<String> traceIds = jdbc.queryForList("""
                SELECT trace_id FROM (
                    SELECT trace_id, MIN(started_at) AS first_at
                    FROM gw_trace_span
                    WHERE tenant_id = ? AND conversation_id = ?
                    GROUP BY trace_id
                ) t
                ORDER BY first_at DESC
                LIMIT ?
                """, String.class, tid, conversationId, n);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String traceId : traceIds) {
            List<SpanRecord> spans = listSpans(tid, traceId);
            if (spans.isEmpty()) {
                continue;
            }
            SpanRecord root = spans.stream()
                    .filter(s -> s.parentSpanId() == null || s.parentSpanId().isBlank())
                    .findFirst()
                    .orElse(spans.get(0));
            long toolCount = spans.stream().filter(s -> SpanKind.TOOL.name().equals(s.kind())).count();
            Map<String, Object> row = new HashMap<>();
            row.put("traceId", traceId);
            row.put("status", root.status());
            row.put("startedAt", root.startedAt());
            row.put("durationMs", root.durationMs());
            row.put("toolCount", toolCount);
            row.put("taskId", root.taskId());
            result.add(row);
        }
        return result;
    }

    private static SpanRecord mapRow(ResultSet rs) throws SQLException {
        Timestamp ended = rs.getTimestamp("ended_at");
        long duration = rs.getLong("duration_ms");
        boolean durationNull = rs.wasNull();
        return new SpanRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("trace_id"),
                rs.getString("parent_span_id"),
                rs.getString("conversation_id"),
                rs.getString("task_id"),
                rs.getString("name"),
                rs.getString("kind"),
                rs.getString("status"),
                rs.getTimestamp("started_at").toInstant(),
                ended == null ? null : ended.toInstant(),
                durationNull ? null : duration,
                rs.getString("attrs_json"));
    }
}
