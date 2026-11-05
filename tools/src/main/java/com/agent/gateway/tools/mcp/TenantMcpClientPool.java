package com.agent.gateway.tools.mcp;

import com.agent.gateway.tenant.TenantIds;
import com.agent.gateway.tools.GatewaySecretProperties;
import com.agent.gateway.tools.McpRegistry;
import com.agent.gateway.tools.McpServerRecord;
import com.agent.gateway.tools.SecretBox;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按租户缓存的 MCP 客户端连接池。
 * <p>所属模块：tools。按需连接各租户 ACTIVE 的 MCP Server，复用客户端并支持配置变更后失效。
 */
@Component
public class TenantMcpClientPool {

    private static final Logger log = LoggerFactory.getLogger(TenantMcpClientPool.class);

    private final McpRegistry mcpRegistry;
    private final SecretBox secretBox;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CachedClient> clients = new ConcurrentHashMap<>();

    /**
     * @param mcpRegistry MCP 仓库，用于列出活跃 Server
     * @param secretProperties 主密钥配置，用于解密 Header
     */
    public TenantMcpClientPool(McpRegistry mcpRegistry, GatewaySecretProperties secretProperties) {
        this.mcpRegistry = mcpRegistry;
        this.secretBox = new SecretBox(secretProperties.masterKeyBytes());
    }

    /**
     * 发现租户下所有 ACTIVE MCP Server 的远端工具。
     *
     * @param tenantId 租户 ID
     * @param requireConfirmDefault 新发现工具的默认确认策略
     * @return 远端工具列表；单 Server 失败时跳过并打日志
     */
    public List<RemoteMcpTool> discover(String tenantId, boolean requireConfirmDefault) {
        String tid = TenantIds.require(tenantId);
        List<RemoteMcpTool> tools = new ArrayList<>();
        for (McpServerRecord server : mcpRegistry.listActive(tid)) {
            try {
                // 复用或新建 MCP 连接，再 listTools，避免每次请求都握手
                McpClientWrapper client = connect(tid, server);
                List<Tool> discovered = client.listTools().block(Duration.ofSeconds(15));
                if (discovered == null) {
                    continue;
                }
                for (Tool tool : discovered) {
                    tools.add(new RemoteMcpTool(server.id(), server.name(), tool, client, requireConfirmDefault));
                }
            } catch (Exception e) {
                log.warn("MCP discover failed tenant={} server={} reason={}", tid, server.name(), e.getMessage());
            }
        }
        return tools;
    }

    /**
     * 获取或建立与指定 MCP Server 的同步客户端。
     *
     * @param tenantId 租户 ID
     * @param server Server 记录
     * @return 已 initialize 的客户端
     * @throws Exception 连接或初始化失败
     */
    private McpClientWrapper connect(String tenantId, McpServerRecord server) throws Exception {
        String key = tenantId + ":" + server.id();
        CachedClient cached = clients.get(key);
        if (cached != null) {
            return cached.client();
        }
        synchronized (this) {
            // 双重检查，避免并发首次连接时对同一 Server 建多条客户端
            cached = clients.get(key);
            if (cached != null) {
                return cached.client();
            }
            // 解密落库密文 Header，才能用真实 Token 建立 MCP 连接
            Map<String, String> headers = decryptHeaders(server.headersCipher());
            String url = normalizeUrl(server.transportUrl());
            McpClientBuilder builder = McpClientBuilder.create(server.name());
            if (url.contains("/sse")) {
                // 路径含 /sse 走 SSE，其余按可流式 HTTP，两种传输不能混用
                builder.sseTransport(url);
            } else {
                builder.streamableHttpTransport(url);
            }
            if (!headers.isEmpty()) {
                builder.headers(headers);
            }
            // 同步构建并 initialize，保证后续 listTools/call 可用
            McpClientWrapper client = builder.buildSync();
            client.initialize().block(Duration.ofSeconds(20));
            clients.put(key, new CachedClient(client));
            return client;
        }
    }

    /**
     * 使指定租户+Server 的缓存连接失效。
     * <p>在 MCP 配置更新或停用后调用，避免继续使用旧凭据。
     *
     * @param tenantId 租户 ID
     * @param serverId MCP Server ID
     */
    public void invalidate(String tenantId, String serverId) {
        String tid = TenantIds.require(tenantId);
        clients.remove(tid + ":" + serverId);
    }

    /**
     * 解密并反序列化 MCP 请求头密文。
     *
     * @param cipher Header 密文；空则返回空 Map
     * @return Header 键值对
     * @throws Exception 解密或 JSON 解析失败
     */
    private Map<String, String> decryptHeaders(String cipher) throws Exception {
        if (cipher == null || cipher.isBlank()) {
            return Map.of();
        }
        String json = secretBox.decrypt(cipher);
        return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
        });
    }

    /**
     * 规范化传输 URL：去掉尾斜杠，缺路径时默认追加 {@code /mcp}。
     *
     * @param raw 原始 URL
     * @return 规范化后的 URL
     */
    private static String normalizeUrl(String raw) {
        String url = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        if (url.endsWith("/sse") || url.endsWith("/mcp")) {
            return url;
        }
        return url + "/mcp";
    }

    /**
     * 缓存中的 MCP 客户端包装。
     *
     * @param client 已初始化客户端
     */
    private record CachedClient(McpClientWrapper client) {
    }
}
