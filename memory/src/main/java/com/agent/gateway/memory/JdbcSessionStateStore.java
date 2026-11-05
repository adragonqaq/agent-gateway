package com.agent.gateway.memory;

import com.agent.gateway.tenant.TenantIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 基于 JDBC 的会话状态仓库。
 * 属于 memory 模块；所有读写以 tenant_id + conversation_id 复合键隔离，写路径为 UPDATE 后 INSERT 的 upsert。
 */
@Repository
public class JdbcSessionStateStore implements SessionStateStore {

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc Spring JDBC 模板
     */
    public JdbcSessionStateStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 幂等保存会话状态：同租户同会话先更新，无行再插入。
     *
     * @param tenantId 租户 ID
     * @param conversationId 会话 ID
     * @param payload 状态正文
     */
    @Override
    public void save(String tenantId, String conversationId, String payload) {
        // 强制租户隔离，拒绝空白 tenant_id 写全局状态
        String tid = TenantIds.require(tenantId);
        // 先 UPDATE 命中行，避免并发下重复插入同键
        int updated = jdbc.update("""
                UPDATE gw_agent_state SET payload = ?, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND conversation_id = ?
                """, payload, tid, conversationId);
        if (updated == 0) {
            // 尚无行时插入，完成按租户隔离的 upsert
            jdbc.update("""
                    INSERT INTO gw_agent_state (tenant_id, conversation_id, payload)
                    VALUES (?, ?, ?)
                    """, tid, conversationId, payload);
        }
    }

    /**
     * 按租户与会话加载状态。
     *
     * @param tenantId 租户 ID
     * @param conversationId 会话 ID
     * @return 首行 payload，无则 empty
     */
    @Override
    public Optional<String> load(String tenantId, String conversationId) {
        // 查询同样绑定 tenant_id，杜绝跨租户读取会话记忆
        String tid = TenantIds.require(tenantId);
        List<String> rows = jdbc.query("""
                SELECT payload FROM gw_agent_state
                WHERE tenant_id = ? AND conversation_id = ?
                """,
                (rs, i) -> rs.getString("payload"),
                tid, conversationId);
        return rows.stream().findFirst();
    }
}
