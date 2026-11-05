package com.agent.gateway.runtime.chat;

import com.agent.gateway.runtime.config.GatewayAgentProperties;
import com.agent.gateway.runtime.tool.CatalogToolkitFactory;
import com.agent.gateway.tools.ResolvedCatalog;
import com.agent.gateway.tools.mcp.RemoteMcpTool;
import com.agent.gateway.tools.mcp.TenantMcpClientPool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 按租户缓存 ReActAgent，并在工具目录指纹变化时重建。
 * <p>所属模块：runtime。仅在 {@code gateway.agent.mode=agentscope} 时装配。
 */
@Component
@ConditionalOnProperty(name = "gateway.agent.mode", havingValue = "agentscope")
public class ReActAgentProvider {

    private final GatewayAgentProperties properties;
    private final OpenAIChatModel chatModel;
    private final InMemoryAgentStateStore stateStore;
    private final CatalogToolkitFactory toolkitFactory;
    private final TenantMcpClientPool mcpClientPool;
    private final ConcurrentHashMap<String, CachedAgent> cache = new ConcurrentHashMap<>();
    private final AtomicInteger rebuilds = new AtomicInteger();

    /**
     * @param properties Agent 人设与迭代上限
     * @param chatModel OpenAI 兼容聊天模型
     * @param stateStore Agent 内存状态存储
     * @param toolkitFactory 由目录与远端 MCP 工具组装 Toolkit
     * @param mcpClientPool 租户 MCP 连接池，重建时重新发现工具
     */
    public ReActAgentProvider(
            GatewayAgentProperties properties,
            OpenAIChatModel chatModel,
            InMemoryAgentStateStore stateStore,
            CatalogToolkitFactory toolkitFactory,
            TenantMcpClientPool mcpClientPool) {
        this.properties = properties;
        this.chatModel = chatModel;
        this.stateStore = stateStore;
        this.toolkitFactory = toolkitFactory;
        this.mcpClientPool = mcpClientPool;
    }

    /**
     * 返回租户当前可用的 Agent；目录指纹变化时同步重建。
     *
     * @param tenantId 租户 ID
     * @param catalog 已解析工具目录
     * @return 含 Agent 与重建代数的快照
     */
    public ActiveSnapshot getOrRebuild(String tenantId, ResolvedCatalog catalog) {
        String fp = catalog.fingerprint();
        CachedAgent current = cache.get(tenantId);
        if (current != null && current.fingerprint().equals(fp)) {
            return new ActiveSnapshot(tenantId, fp, current.agent(), rebuilds.get());
        }
        synchronized (this) {
            // 双重检查：并发重建时只创建一个 Agent，避免同一租户两套工具集
            current = cache.get(tenantId);
            if (current != null && current.fingerprint().equals(fp)) {
                return new ActiveSnapshot(tenantId, fp, current.agent(), rebuilds.get());
            }
            // 重建前重新发现远端工具，Toolkit 才能挂上最新 schema 与客户端
            List<RemoteMcpTool> remotes = mcpClientPool.discover(tenantId, true);
            ReActAgent agent = ReActAgent.builder()
                    .name("AgentGateway")
                    .sysPrompt(properties.getPersona())
                    .model(chatModel)
                    .toolkit(toolkitFactory.build(catalog, remotes))
                    .maxIters(properties.getMaxIters())
                    .maxRetries(properties.getMaxRetries())
                    // 确认中断后可恢复挂起的工具调用，避免整轮状态丢失
                    .enablePendingToolRecovery(true)
                    .stateStore(stateStore)
                    .build();
            rebuilds.incrementAndGet();
            cache.put(tenantId, new CachedAgent(fp, agent));
            return new ActiveSnapshot(tenantId, fp, agent, rebuilds.get());
        }
    }

    /**
     * @return 进程内累计的 Agent 重建次数
     */
    public int rebuildCount() {
        return rebuilds.get();
    }

    /**
     * 当前生效的 Agent 快照。
     * <p>所属模块：runtime。
     *
     * @param tenantId 租户 ID
     * @param fingerprint 构建该 Agent 时的目录指纹
     * @param agent 可复用的 ReActAgent
     * @param rebuildGeneration 全局重建代数
     */
    public record ActiveSnapshot(String tenantId, String fingerprint, ReActAgent agent, int rebuildGeneration) {
    }

    /**
     * 缓存项：指纹与对应 Agent。
     * <p>所属模块：runtime。指纹用于判断目录是否仍匹配。
     *
     * @param fingerprint 目录指纹
     * @param agent 已构建的 Agent
     */
    private record CachedAgent(String fingerprint, ReActAgent agent) {
    }
}
