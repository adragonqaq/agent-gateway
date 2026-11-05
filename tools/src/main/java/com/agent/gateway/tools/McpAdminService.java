package com.agent.gateway.tools;

import com.agent.gateway.tenant.TenantIds;
import com.agent.gateway.tools.mcp.TenantMcpClientPool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 租户 MCP Server 管理服务。
 * <p>所属模块：tools。负责登记/停用 MCP，并在变更后使连接池缓存失效，避免继续使用旧 Header 或 URL。
 */
@Service
public class McpAdminService {

    private final McpRegistry mcpRegistry;
    private final SecretBox secretBox;
    private final ObjectProvider<TenantMcpClientPool> mcpClientPool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param mcpRegistry MCP 持久化仓库
     * @param secretProperties 主密钥配置，用于加密 Header
     * @param mcpClientPool MCP 连接池（可选；单元测试可不注入）
     */
    public McpAdminService(
            McpRegistry mcpRegistry,
            GatewaySecretProperties secretProperties,
            ObjectProvider<TenantMcpClientPool> mcpClientPool) {
        this.mcpRegistry = mcpRegistry;
        // 仅用主密钥构造加密盒，避免明文 Header 落库
        this.secretBox = new SecretBox(secretProperties.masterKeyBytes());
        this.mcpClientPool = mcpClientPool;
    }

    /**
     * 登记或更新租户的 MCP Server。
     *
     * @param tenantId 目标租户 ID
     * @param serverId 已有 Server ID；为空则新建
     * @param name 展示名
     * @param transportUrl MCP 传输地址（SSE 或 streamable HTTP）
     * @param headers 请求头明文；为空表示不改动已有密文
     * @param status ACTIVE / DISABLED；默认 ACTIVE
     * @return 持久化后的记录（不含明文 Header）
     */
    public McpServerRecord upsert(
            String tenantId,
            String serverId,
            String name,
            String transportUrl,
            Map<String, String> headers,
            String status) {
        String tid = TenantIds.require(tenantId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (transportUrl == null || transportUrl.isBlank()) {
            throw new IllegalArgumentException("transportUrl required");
        }
        String id = (serverId == null || serverId.isBlank())
                ? "mcp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                : serverId;
        String resolvedStatus = (status == null || status.isBlank()) ? "ACTIVE" : status.trim().toUpperCase();

        String cipher;
        if (headers != null && !headers.isEmpty()) {
            // 入库前加密，防止敏感 Token 以明文出现在数据库
            cipher = encryptHeaders(headers);
        } else {
            // 更新且未传 headers 时保留原密文，避免误清空鉴权信息
            cipher = mcpRegistry.find(tid, id).map(McpServerRecord::headersCipher).orElse(null);
        }

        McpServerRecord record = new McpServerRecord(id, tid, name.trim(), transportUrl.trim(), cipher, resolvedStatus);
        // 按 tenant_id + id 幂等写入，隔离边界由仓库强制
        mcpRegistry.upsert(record);
        TenantMcpClientPool pool = mcpClientPool.getIfAvailable();
        if (pool != null) {
            // 配置变更后丢掉旧连接，下次 discover 用新 URL/Header
            pool.invalidate(tid, id);
        }
        return record;
    }

    /**
     * 列出租户当前 ACTIVE 的 MCP Server（不含解密 Header）。
     *
     * @param tenantId 租户 ID
     * @return 活跃 Server 列表
     */
    public List<McpServerRecord> listActive(String tenantId) {
        return mcpRegistry.listActive(TenantIds.require(tenantId));
    }

    /**
     * 将 MCP Server 标记为 DISABLED，并失效连接缓存。
     *
     * @param tenantId 租户 ID
     * @param serverId Server ID
     * @return 更新后的记录
     */
    public McpServerRecord disable(String tenantId, String serverId) {
        String tid = TenantIds.require(tenantId);
        McpServerRecord existing = mcpRegistry.find(tid, serverId)
                .orElseThrow(() -> new IllegalArgumentException("MCP server not found"));
        McpServerRecord disabled = new McpServerRecord(
                existing.id(), existing.tenantId(), existing.name(),
                existing.transportUrl(), existing.headersCipher(), "DISABLED");
        mcpRegistry.upsert(disabled);
        TenantMcpClientPool pool = mcpClientPool.getIfAvailable();
        if (pool != null) {
            // 停用后立即失效连接，避免后续请求仍打到已禁用 Server
            pool.invalidate(tid, serverId);
        }
        return disabled;
    }

    /**
     * 将 Header Map 序列化为 JSON 再 AES-GCM 加密。
     *
     * @param headers 明文 Header
     * @return 密文字符串
     * @throws IllegalStateException 序列化或加密失败
     */
    private String encryptHeaders(Map<String, String> headers) {
        try {
            Map<String, String> copy = new LinkedHashMap<>(headers);
            return secretBox.encrypt(objectMapper.writeValueAsString(copy));
        } catch (Exception e) {
            throw new IllegalStateException("failed to encrypt MCP headers", e);
        }
    }
}
