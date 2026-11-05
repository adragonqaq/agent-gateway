package com.nageoffer.ai.gateway.tools;

import com.nageoffer.ai.gateway.tenant.TenantIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcMcpRegistry implements McpRegistry {

    private final JdbcTemplate jdbc;

    public JdbcMcpRegistry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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
            jdbc.update("""
                    INSERT INTO gw_mcp_server (id, tenant_id, name, transport_url, headers_cipher, status)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    server.id(), tenantId, server.name(), server.transportUrl(),
                    server.headersCipher(), server.status());
        }
    }

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
