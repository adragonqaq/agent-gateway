package com.agent.gateway.tools;

import com.agent.gateway.tenant.TenantIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 基于 JDBC 的 MCP Server 仓库实现。
 * <p>所属模块：tools。读写 {@code gw_mcp_server} 表，并强制租户隔离。
 */
@Repository
public class JdbcMcpRegistry implements McpRegistry {

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc Spring JDBC 模板
     */
    public JdbcMcpRegistry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * {@inheritDoc}
     * <p>先 UPDATE，无行受影响再 INSERT，保证幂等。
     */
    @Override
    public void upsert(McpServerRecord server) {
        String tenantId = TenantIds.require(server.tenantId());
        int updated = jdbc.update("""
                UPDATE gw_mcp_server
                SET name = ?, transport_url = ?, headers_cipher = ?, status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND tenant_id = ?
                """,
                server.name(), server.transportUrl(), server.headersCipher(), server.status(),
                server.id(), tenantId);
        if (updated == 0) {
            // 该租户下尚无此 id 才插入，避免跨租户覆盖，并保证重复登记幂等
            jdbc.update("""
                    INSERT INTO gw_mcp_server (id, tenant_id, name, transport_url, headers_cipher, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    server.id(), tenantId, server.name(), server.transportUrl(),
                    server.headersCipher(), server.status());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<McpServerRecord> listActive(String tenantId) {
        String id = TenantIds.require(tenantId);
        return jdbc.query("""
                SELECT id, tenant_id, name, transport_url, headers_cipher, status
                FROM gw_mcp_server
                WHERE tenant_id = ? AND status = 'ACTIVE'
                ORDER BY name
                """,
                (rs, i) -> new McpServerRecord(
                        rs.getString("id"),
                        rs.getString("tenant_id"),
                        rs.getString("name"),
                        rs.getString("transport_url"),
                        rs.getString("headers_cipher"),
                        rs.getString("status")),
                id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<McpServerRecord> find(String tenantId, String serverId) {
        String tid = TenantIds.require(tenantId);
        List<McpServerRecord> rows = jdbc.query("""
                SELECT id, tenant_id, name, transport_url, headers_cipher, status
                FROM gw_mcp_server
                WHERE tenant_id = ? AND id = ?
                """,
                (rs, i) -> new McpServerRecord(
                        rs.getString("id"),
                        rs.getString("tenant_id"),
                        rs.getString("name"),
                        rs.getString("transport_url"),
                        rs.getString("headers_cipher"),
                        rs.getString("status")),
                tid, serverId);
        return rows.stream().findFirst();
    }
}
