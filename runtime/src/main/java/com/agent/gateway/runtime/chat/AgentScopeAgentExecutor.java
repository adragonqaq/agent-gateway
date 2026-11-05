package com.agent.gateway.runtime.chat;

import com.agent.gateway.runtime.ConfirmGate;
import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tools.ResolvedCatalog;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.message.ToolUseBlock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 基于 AgentScope ReAct 的聊天执行器。
 * <p>所属模块：runtime。仅在 {@code gateway.agent.mode=agentscope} 时装配，同步收集流式事件；
 * 收到 RequireUserConfirmEvent 时写入 ConfirmGate，SSE confirm 携带 confirmId。
 */
@Component
@ConditionalOnProperty(name = "gateway.agent.mode", havingValue = "agentscope")
public class AgentScopeAgentExecutor implements AgentExecutor {

    private final ReActAgentProvider agentProvider;
    private final ConfirmGate confirmGate;

    /**
     * @param agentProvider 按租户缓存并按目录指纹重建 ReActAgent
     * @param confirmGate 人工确认闸门
     */
    public AgentScopeAgentExecutor(ReActAgentProvider agentProvider, ConfirmGate confirmGate) {
        this.agentProvider = agentProvider;
        this.confirmGate = confirmGate;
    }

    /**
     * {@inheritDoc}
     * <p>同步订阅 AgentScope 事件流，成功后写入紧凑会话快照。
     */
    @Override
    public void run(TenantContext tenant, String taskId, String conversationId, String message,
                    ResolvedCatalog catalog, String priorState,
                    Consumer<AgentEvent> events, Consumer<String> stateSaver) {
        // 指纹未变则复用 Agent，变了才重建，避免每次请求都丢上下文
        ReActAgentProvider.ActiveSnapshot snapshot = agentProvider.getOrRebuild(tenant.tenantId(), catalog);
        ReActAgent agent = snapshot.agent();
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .userId(tenant.tenantId())
                .sessionId(conversationId)
                .build();

        StringJoiner answer = new StringJoiner("");
        AtomicReference<Throwable> error = new AtomicReference<>();
        try {
            // 网关按同步一轮收集事件；超时避免调用线程被挂死
            agent.streamEvents(message, runtimeContext)
                    .doOnNext(scopeEvent -> {
                        if (scopeEvent instanceof RequireUserConfirmEvent confirmEvent) {
                            // AgentScope 只发出需确认信号；网关负责落 ConfirmGate 并带上 confirmId
                            events.accept(mapConfirm(tenant.tenantId(), taskId, confirmEvent));
                            return;
                        }
                        AgentScopeEventMapper.map(scopeEvent).ifPresent(mapped -> {
                            events.accept(mapped);
                            if ("answer".equals(mapped.type())) {
                                answer.add(mapped.data());
                            }
                        });
                    })
                    .doOnError(error::set)
                    .blockLast(Duration.ofMinutes(5));
        } catch (RuntimeException ex) {
            error.set(ex);
        }

        if (error.get() != null) {
            // 事件流需要显式失败标记，同时抛出让上层跳过成功用量
            events.accept(new AgentEvent("error", error.get().getMessage()));
            events.accept(new AgentEvent("done", "error"));
            throw new IllegalStateException("AgentScope run failed", error.get());
        }

        events.accept(new AgentEvent("done", "ok"));
        String payload = "{\"last\":\"" + escape(answer.toString()) + "\",\"prior\":"
                + (priorState == null ? "\"\"" : priorState) + "}";
        // 先落紧凑快照，后续 Agent 自身状态保存失败也不丢本轮回答
        stateSaver.accept(payload);
        try {
            agent.saveAgentState(tenant.tenantId(), conversationId);
        } catch (Exception ignored) {
            // 上方已把紧凑快照交给 SessionStateStore，此处失败不阻断本轮
        }
    }

    /**
     * 将 AgentScope 确认事件转为带 confirmId 的网关 confirm 事件。
     */
    private AgentEvent mapConfirm(String tenantId, String taskId, RequireUserConfirmEvent event) {
        List<ConfirmPayloadFactory.ToolCall> tools = new ArrayList<>();
        List<ToolUseBlock> calls = event.getToolCalls() == null ? List.of() : event.getToolCalls();
        for (ToolUseBlock call : calls) {
            tools.add(new ConfirmPayloadFactory.ToolCall(call.getName(), toJson(call.getInput())));
        }
        if (tools.isEmpty()) {
            tools.add(new ConfirmPayloadFactory.ToolCall("unknown", "{}"));
        }
        String data = ConfirmPayloadFactory.suspendAndSerialize(
                confirmGate, tenantId, taskId, event.getReplyId(), tools);
        return new AgentEvent("confirm", data);
    }

    /**
     * 工具入参转 JSON；失败时退回空对象，避免整轮中断。
     */
    private static String toJson(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return "{}";
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(input);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 转义拼进 JSON 字符串的回答，避免引号和换行破坏快照结构。
     *
     * @param raw 原始回答文本
     * @return 可嵌入 JSON 字符串的文本
     */
    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "'").replace("\n", "\\n");
    }
}
