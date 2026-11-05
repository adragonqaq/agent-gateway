package com.nageoffer.ai.gateway.memory;

import com.nageoffer.ai.gateway.tenant.TenantIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcSessionStateStore implements SessionStateStore {

    private final JdbcTemplate jdbc;

    public JdbcSessionStateStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(String tenantId, String conversationId, String payload) {
        String tid = TenantIds.require(tenantId);
        int updated = jdbc.update("""
                UPDATE gw_agent_state SET payload = ?, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND conversation_id = ?
                """, payload, tid, conversationId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO gw_agent_state (tenant_id, conversation_id, payload)
                    VALUES (?, ?, ?)
                    """, tid, conversationId, payload);
        }
    }

    @Override
    public Optional<String> load(String tenantId, String conversationId) {
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
