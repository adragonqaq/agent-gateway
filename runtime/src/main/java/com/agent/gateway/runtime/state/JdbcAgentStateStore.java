package com.agent.gateway.runtime.state;

import com.agent.gateway.tenant.TenantIds;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.State;
import io.agentscope.core.util.JsonUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * AgentScope 状态的 JDBC 实现。
 * <p>所属模块：runtime。按租户(userId)+会话(sessionId)+state_key 落库，支撑 cancel 后 pending tool recovery。
 */
public class JdbcAgentStateStore implements AgentStateStore {

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc JDBC 模板
     */
    public JdbcAgentStateStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(String userId, String sessionId, String key, State value) {
        upsert(userId, sessionId, key, JsonUtils.getJsonCodec().toJson(value));
    }

    @Override
    public void save(String userId, String sessionId, String key, List<? extends State> values) {
        upsert(userId, sessionId, key, JsonUtils.getJsonCodec().toJson(values));
    }

    @Override
    public <T extends State> Optional<T> get(String userId, String sessionId, String key, Class<T> type) {
        String payload = queryPayload(userId, sessionId, key);
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(JsonUtils.getJsonCodec().fromJson(payload, type));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends State> List<T> getList(String userId, String sessionId, String key, Class<T> itemType) {
        String payload = queryPayload(userId, sessionId, key);
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        List<?> raw = JsonUtils.getJsonCodec().fromJson(payload, List.class);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<T> result = new ArrayList<>(raw.size());
        for (Object item : raw) {
            result.add(JsonUtils.getJsonCodec().convertValue(item, itemType));
        }
        return result;
    }

    @Override
    public boolean exists(String userId, String sessionId) {
        String tid = TenantIds.require(userId);
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM gw_agentscope_state
                WHERE tenant_id = ? AND conversation_id = ?
                """, Integer.class, tid, sessionId);
        return n != null && n > 0;
    }

    @Override
    public void delete(String userId, String sessionId) {
        String tid = TenantIds.require(userId);
        jdbc.update("DELETE FROM gw_agentscope_state WHERE tenant_id = ? AND conversation_id = ?",
                tid, sessionId);
    }

    @Override
    public void delete(String userId, String sessionId, String key) {
        String tid = TenantIds.require(userId);
        jdbc.update("""
                DELETE FROM gw_agentscope_state
                WHERE tenant_id = ? AND conversation_id = ? AND state_key = ?
                """, tid, sessionId, key);
    }

    @Override
    public Set<String> listSessionIds(String userId) {
        String tid = TenantIds.require(userId);
        return new LinkedHashSet<>(jdbc.queryForList("""
                SELECT DISTINCT conversation_id FROM gw_agentscope_state WHERE tenant_id = ?
                """, String.class, tid));
    }

    private void upsert(String userId, String sessionId, String key, String payload) {
        String tid = TenantIds.require(userId);
        int updated = jdbc.update("""
                UPDATE gw_agentscope_state SET payload = ?, updated_at = CURRENT_TIMESTAMP
                WHERE tenant_id = ? AND conversation_id = ? AND state_key = ?
                """, payload, tid, sessionId, key);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO gw_agentscope_state (tenant_id, conversation_id, state_key, payload)
                    VALUES (?, ?, ?, ?)
                    """, tid, sessionId, key, payload);
        }
    }

    private String queryPayload(String userId, String sessionId, String key) {
        String tid = TenantIds.require(userId);
        List<String> rows = jdbc.query("""
                SELECT payload FROM gw_agentscope_state
                WHERE tenant_id = ? AND conversation_id = ? AND state_key = ?
                """, (rs, i) -> rs.getString("payload"), tid, sessionId, key);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
