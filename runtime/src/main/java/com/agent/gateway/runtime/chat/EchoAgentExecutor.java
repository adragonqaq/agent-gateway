package com.agent.gateway.runtime.chat;

import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tools.ResolvedCatalog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 回显执行器，不调用大模型。
 * <p>所属模块：runtime。默认模式，用于本地联调：回放目录中的工具名并回显用户消息。
 */
@Component
@ConditionalOnProperty(name = "gateway.agent.mode", havingValue = "echo", matchIfMissing = true)
public class EchoAgentExecutor implements AgentExecutor {

    private final CatalogFingerprintTracker fingerprintTracker;

    /**
     * @param fingerprintTracker 目录指纹跟踪，便于断言工具集变化会触发重建计数
     */
    public EchoAgentExecutor(CatalogFingerprintTracker fingerprintTracker) {
        this.fingerprintTracker = fingerprintTracker;
    }

    /**
     * {@inheritDoc}
     * <p>产出 thinking、tool、answer、done 事件，并把本轮消息写入会话快照。
     */
    @Override
    public void run(TenantContext tenant, String conversationId, String message,
                    ResolvedCatalog catalog, String priorState,
                    Consumer<AgentEvent> events, Consumer<String> stateSaver) {
        // 即使不建 Agent，也要刷新指纹，目录变更才能被观测到
        fingerprintTracker.getOrRebuild(tenant.tenantId(), catalog);
        events.accept(new AgentEvent("thinking", "planning with " + catalog.tools().size() + " tools"));
        String toolNames = catalog.tools().stream().map(CatalogToolName::of).collect(Collectors.joining(","));
        if (!toolNames.isBlank()) {
            events.accept(new AgentEvent("tool", toolNames));
        }
        events.accept(new AgentEvent("answer", "echo: " + message));
        events.accept(new AgentEvent("done", "ok"));
        // 引号换成单引号，避免快照 JSON 被用户输入截断
        stateSaver.accept("{\"last\":\"" + message.replace("\"", "'") + "\"}");
    }

    /**
     * 从目录条目取出工具名，避免在流里直接依赖记录访问器的可读性。
     * <p>所属模块：runtime。
     */
    private static final class CatalogToolName {

        /**
         * @param tool 目录工具
         * @return 工具名称
         */
        static String of(com.agent.gateway.tools.CatalogTool tool) {
            return tool.name();
        }
    }
}
