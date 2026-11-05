package com.agent.gateway.runtime.chat;

import com.agent.gateway.runtime.ConfirmGate;
import com.agent.gateway.runtime.PendingConfirmation;
import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tools.CatalogTool;
import com.agent.gateway.tools.ResolvedCatalog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 回显执行器，不调用大模型。
 * <p>所属模块：runtime。默认模式，用于本地联调：回放目录中的工具名并回显用户消息；
 * 对 requireConfirm=true 的工具会挂 ConfirmGate 并发出 confirm 事件；
 * approve/deny 后以确定性 answer 续跑。
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
     */
    @Override
    public void run(TenantContext tenant, String taskId, String conversationId, String message,
                    ResolvedCatalog catalog, String priorState,
                    Consumer<AgentEvent> events, Consumer<String> stateSaver) {
        long started = System.nanoTime();
        fingerprintTracker.getOrRebuild(tenant.tenantId(), catalog);
        events.accept(new AgentEvent("thinking", "planning with " + catalog.tools().size() + " tools"));

        List<ConfirmPayloadFactory.ToolCall> needingConfirm = catalog.tools().stream()
                .filter(CatalogTool::requireConfirm)
                .filter(CatalogTool::available)
                .map(t -> new ConfirmPayloadFactory.ToolCall(t.name(), "{}"))
                .toList();
        if (!needingConfirm.isEmpty()) {
            // echo 也走真实 ConfirmGate，便于无模型联调 approve/deny 续跑
            String payload = ConfirmPayloadFactory.suspendAndSerialize(
                    confirmGate, tenant.tenantId(), taskId, conversationId, null, needingConfirm);
            events.accept(new AgentEvent("confirm", payload));
            // 挂起本轮，不发最终 answer/done；由 resumeConfirm 收口
            stateSaver.accept("{\"awaitingConfirm\":true,\"last\":\"" + message.replace("\"", "'") + "\"}");
            return;
        }

        // 结构化 tool 事件，便于前端活动条在 echo 模式也可演示
        List<CatalogTool> available = catalog.tools().stream().filter(CatalogTool::available).toList();
        int i = 0;
        for (CatalogTool tool : available) {
            String id = "echo-" + (++i);
            events.accept(AgentScopeEventMapper.toolStart(id, tool.name()));
            events.accept(AgentScopeEventMapper.toolArgs(id, tool.name(), "{}"));
            events.accept(AgentScopeEventMapper.toolEnd(id, tool.name(), "ok", 1L, "{}"));
        }

        events.accept(new AgentEvent("answer", "echo: " + message));
        events.accept(AgentScopeEventMapper.done("ok", elapsedMs(started)));
        stateSaver.accept("{\"last\":\"" + message.replace("\"", "'") + "\"}");
    }

    /**
     * {@inheritDoc}
     * <p>按批准/拒绝输出确定性文案，模拟工具已执行或已取消。
     */
    @Override
    public void resumeConfirm(TenantContext tenant, String resumeTaskId, List<PendingConfirmation> settled,
                              boolean approved, ResolvedCatalog catalog,
                              Consumer<AgentEvent> events, Consumer<String> stateSaver) {
        long started = System.nanoTime();
        String tools = settled.stream().map(PendingConfirmation::toolName).collect(Collectors.joining(","));
        if (approved) {
            int i = 0;
            for (PendingConfirmation row : settled) {
                String id = "echo-resume-" + (++i);
                events.accept(AgentScopeEventMapper.toolStart(id, row.toolName()));
                events.accept(AgentScopeEventMapper.toolArgs(id, row.toolName(), row.argsJson()));
                events.accept(AgentScopeEventMapper.toolEnd(id, row.toolName(), "ok", 1L, row.argsJson()));
            }
            events.accept(new AgentEvent("answer", "confirmed: " + tools));
        } else {
            events.accept(new AgentEvent("answer", "denied: " + tools));
        }
        events.accept(AgentScopeEventMapper.done("ok", elapsedMs(started)));
        String conv = settled.isEmpty() ? "" : settled.get(0).conversationId();
        stateSaver.accept("{\"lastConfirm\":\"" + (approved ? "approved" : "denied")
                + "\",\"tools\":\"" + tools.replace("\"", "'") + "\",\"conversationId\":\""
                + (conv == null ? "" : conv) + "\"}");
    }

    private static long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}
