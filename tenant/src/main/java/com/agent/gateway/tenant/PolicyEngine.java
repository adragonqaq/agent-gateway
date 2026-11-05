package com.agent.gateway.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户工具策略引擎：按租户加载/写入工具 ALLOW·DENY 与确认门配置。
 * 属于 tenant 模块；所有 SQL 以 tenant_id 为隔离键，禁止跨租户读写策略。
 */
@Component
public class PolicyEngine {

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc Spring JDBC 模板
     */
    public PolicyEngine(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 加载指定租户全部工具策略，按工具名建索引。
     *
     * @param tenantId 租户 ID
     * @return toolName → {@link ToolPolicy}
     */
    public Map<String, ToolPolicy> policiesFor(String tenantId) {
        // 强制租户边界，防止空 tenant_id 扫到其他租户策略
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

    /**
     * 幂等写入工具策略：先 UPDATE，无行则 INSERT。
     *
     * @param tenantId 租户 ID
     * @param policy 待写入策略
     */
    public void upsert(String tenantId, ToolPolicy policy) {
        String id = TenantIds.require(tenantId);
        // 先更新同租户同工具行，保证幂等且不误插重复键
        int updated = jdbc.update("""
                UPDATE gw_tool_policy SET effect = ?, require_confirm = ?
                WHERE tenant_id = ? AND tool_name = ?
                """,
                policy.effect(), policy.requireConfirm(), id, policy.toolName());
        if (updated == 0) {
            // 无既有行时再插入，完成 upsert
            jdbc.update("""
                    INSERT INTO gw_tool_policy (tenant_id, tool_name, effect, require_confirm)
                    VALUES (?, ?, ?, ?)
                    """,
                    id, policy.toolName(), policy.effect(), policy.requireConfirm());
        }
    }
}
