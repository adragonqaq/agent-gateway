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
 * Connects tenant MCP servers on demand and caches wrappers by tenant+server.
 */
@Component
public class TenantMcpClientPool {

    private static final Logger log = LoggerFactory.getLogger(TenantMcpClientPool.class);

    private final McpRegistry mcpRegistry;
    private final SecretBox secretBox;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CachedClient> clients = new ConcurrentHashMap<>();

    public TenantMcpClientPool(McpRegistry mcpRegistry, GatewaySecretProperties secretProperties) {
        this.mcpRegistry = mcpRegistry;
        this.secretBox = new SecretBox(secretProperties.masterKeyBytes());
    }

    public List<RemoteMcpTool> discover(String tenantId, boolean requireConfirmDefault) {
        String tid = TenantIds.require(tenantId);
        List<RemoteMcpTool> tools = new ArrayList<>();
        for (McpServerRecord server : mcpRegistry.listActive(tid)) {
            try {
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

    private McpClientWrapper connect(String tenantId, McpServerRecord server) throws Exception {
        String key = tenantId + ":" + server.id();
        CachedClient cached = clients.get(key);
        if (cached != null) {
            return cached.client();
        }
        synchronized (this) {
            cached = clients.get(key);
            if (cached != null) {
                return cached.client();
            }
            Map<String, String> headers = decryptHeaders(server.headersCipher());
            String url = normalizeUrl(server.transportUrl());
            McpClientBuilder builder = McpClientBuilder.create(server.name());
            if (url.contains("/sse")) {
                builder.sseTransport(url);
            } else {
                builder.streamableHttpTransport(url);
            }
            if (!headers.isEmpty()) {
                builder.headers(headers);
            }
            McpClientWrapper client = builder.buildSync();
            client.initialize().block(Duration.ofSeconds(20));
            clients.put(key, new CachedClient(client));
            return client;
        }
    }

    private Map<String, String> decryptHeaders(String cipher) throws Exception {
        if (cipher == null || cipher.isBlank()) {
            return Map.of();
        }
        String json = secretBox.decrypt(cipher);
        return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
        });
    }

    private static String normalizeUrl(String raw) {
        String url = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        if (url.endsWith("/sse") || url.endsWith("/mcp")) {
            return url;
        }
        return url + "/mcp";
    }

    private record CachedClient(McpClientWrapper client) {
    }
}
