package com.agent.gateway.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PolicyEngine {

    private final JdbcTemplate jdbc;

    public PolicyEngine(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, ToolPolicy> policiesFor(String tenantId) {
        String id = TenantIds.require(tenantId);
        List<ToolPolicy> rows = jdbc.query("""
                SELECT tool_name, effect, require_confirm
                FROM gw_tool_policy WHERE tenant_id = ?
                """,
                (rs, i) -> new ToolPolicy(
                        rs.getString("tool_name"),
                        rs.getString("effect"),
                        rs.getBoolean("require_confirm")),
                id);
        Map<String, ToolPolicy> map = new HashMap<>();
        for (ToolPolicy p : rows) {
            map.put(p.toolName(), p);
        }
        return map;
    }

    public void upsert(String tenantId, ToolPolicy policy) {
        String id = TenantIds.require(tenantId);
        int updated = jdbc.update("""
                UPDATE gw_tool_policy SET effect = ?, require_confirm = ?
                WHERE tenant_id = ? AND tool_name = ?
                """,
                policy.effect(), policy.requireConfirm(), id, policy.toolName());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO gw_tool_policy (tenant_id, tool_name, effect, require_confirm)
                    VALUES (?, ?, ?, ?)
                    """,
                    id, policy.toolName(), policy.effect(), policy.requireConfirm());
        }
    }
}
