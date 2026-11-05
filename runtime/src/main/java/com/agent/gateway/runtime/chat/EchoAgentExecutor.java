package com.agent.gateway.runtime.chat;

import com.agent.gateway.runtime.ConfirmGate;
import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tools.CatalogTool;
import com.agent.gateway.tools.ResolvedCatalog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 回显执行器，不调用大模型。
 * <p>所属模块：runtime。默认模式，用于本地联调：回放目录中的工具名并回显用户消息；
 * 对 requireConfirm=true 的工具会挂 ConfirmGate 并发出 confirm 事件。
 */
@Component
@ConditionalOnProperty(name = "gateway.agent.mode", havingValue = "echo", matchIfMissing = true)
public class EchoAgentExecutor implements AgentExecutor {

    private final CatalogFingerprintTracker fingerprintTracker;
    private final ConfirmGate confirmGate;

    /**
     * @param fingerprintTracker 目录指纹跟踪，便于断言工具集变化会触发重建计数
     * @param confirmGate 确认闸门，用于演示挂起需确认的工具
     */
    public EchoAgentExecutor(CatalogFingerprintTracker fingerprintTracker, ConfirmGate confirmGate) {
        this.fingerprintTracker = fingerprintTracker;
        this.confirmGate = confirmGate;
    }

    /**
     * {@inheritDoc}
     * <p>产出 thinking、tool、confirm（如有）、answer、done 事件，并把本轮消息写入会话快照。
     */
    @Override
    public void run(TenantContext tenant, String taskId, String conversationId, String message,
                    ResolvedCatalog catalog, String priorState,
                    Consumer<AgentEvent> events, Consumer<String> stateSaver) {
        // 即使不建 Agent，也要刷新指纹，目录变更才能被观测到
        fingerprintTracker.getOrRebuild(tenant.tenantId(), catalog);
        events.accept(new AgentEvent("thinking", "planning with " + catalog.tools().size() + " tools"));
        String toolNames = catalog.tools().stream().map(CatalogTool::name).collect(Collectors.joining(","));
        if (!toolNames.isBlank()) {
            events.accept(new AgentEvent("tool", toolNames));
        }

        List<ConfirmPayloadFactory.ToolCall> needingConfirm = catalog.tools().stream()
                .filter(CatalogTool::requireConfirm)
                .filter(CatalogTool::available)
                .map(t -> new ConfirmPayloadFactory.ToolCall(t.name(), "{}"))
                .toList();
        if (!needingConfirm.isEmpty()) {
            // echo 也走真实 ConfirmGate，便于无模型联调 approve/deny API
            String payload = ConfirmPayloadFactory.suspendAndSerialize(
                    confirmGate, tenant.tenantId(), taskId, null, needingConfirm);
            events.accept(new AgentEvent("confirm", payload));
        }

        events.accept(new AgentEvent("answer", "echo: " + message));
        events.accept(new AgentEvent("done", "ok"));
        // 引号换成单引号，避免快照 JSON 被用户输入截断
        stateSaver.accept("{\"last\":\"" + message.replace("\"", "'") + "\"}");
    }
}
