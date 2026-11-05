package com.agent.gateway.runtime.chat;

import com.agent.gateway.runtime.ConfirmGate;
import com.agent.gateway.runtime.PendingConfirmation;
import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tools.ResolvedCatalog;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
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
 * <p>所属模块：runtime。仅在 {@code gateway.agent.mode=agentscope} 时装配；
 * 收到 RequireUserConfirmEvent 时写入 ConfirmGate；approve/deny 后以
 * {@link Msg#METADATA_CONFIRM_RESULTS} 续跑。
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
     */
    @Override
    public void run(TenantContext tenant, String taskId, String conversationId, String message,
                    ResolvedCatalog catalog, String priorState,
                    Consumer<AgentEvent> events, Consumer<String> stateSaver) {
        ReActAgentProvider.ActiveSnapshot snapshot = agentProvider.getOrRebuild(tenant.tenantId(), catalog);
        ReActAgent agent = snapshot.agent();
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .userId(tenant.tenantId())
                .sessionId(conversationId)
                .build();

        StringJoiner answer = new StringJoiner("");
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Boolean> awaitingConfirm = new AtomicReference<>(false);
        try {
            agent.streamEvents(message, runtimeContext)
                    .doOnNext(scopeEvent -> {
                        if (scopeEvent instanceof RequireUserConfirmEvent confirmEvent) {
                            // 落 ConfirmGate 并标记本轮挂起，不再发最终 done
                            events.accept(mapConfirm(tenant.tenantId(), taskId, conversationId, confirmEvent));
                            awaitingConfirm.set(true);
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
            events.accept(new AgentEvent("error", error.get().getMessage()));
            events.accept(new AgentEvent("done", "error"));
            throw new IllegalStateException("AgentScope run failed", error.get());
        }

        // 等待确认时保留 Agent 状态，由 resumeConfirm 继续
        if (Boolean.TRUE.equals(awaitingConfirm.get())) {
            stateSaver.accept("{\"awaitingConfirm\":true,\"prior\":"
                    + (priorState == null ? "\"\"" : priorState) + "}");
            try {
                agent.saveAgentState(tenant.tenantId(), conversationId);
            } catch (Exception ignored) {
                // 状态保存失败不阻断挂起；续跑时若无 ASKING 会报失效
            }
            return;
        }

        events.accept(new AgentEvent("done", "ok"));
        String payload = "{\"last\":\"" + escape(answer.toString()) + "\",\"prior\":"
                + (priorState == null ? "\"\"" : priorState) + "}";
        stateSaver.accept(payload);
        try {
            agent.saveAgentState(tenant.tenantId(), conversationId);
        } catch (Exception ignored) {
            // 紧凑快照已写入
        }
    }

    /**
     * {@inheritDoc}
     * <p>从 Agent 状态读取 ASKING 工具，组装 ConfirmResult 后空正文续跑。
     */
    @Override
    public void resumeConfirm(TenantContext tenant, String resumeTaskId, List<PendingConfirmation> settled,
                              boolean approved, ResolvedCatalog catalog,
                              Consumer<AgentEvent> events, Consumer<String> stateSaver) {
        if (settled.isEmpty()) {
            throw new IllegalStateException("no settled confirmations");
        }
        String conversationId = settled.get(0).conversationId();
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalStateException("confirmation missing conversationId");
        }

        ReActAgentProvider.ActiveSnapshot snapshot = agentProvider.getOrRebuild(tenant.tenantId(), catalog);
        ReActAgent agent = snapshot.agent();

        // 工具入参以 Agent 状态为准，防止客户端篡改 args
        List<ToolUseBlock> asking = askingToolCalls(agent.getAgentState(tenant.tenantId(), conversationId).getContext());
        if (asking.isEmpty()) {
            agent.clearStateCache(tenant.tenantId(), conversationId);
            throw new IllegalStateException("待确认的操作已失效，请重新提问");
        }
        List<ConfirmResult> results = asking.stream()
                .map(toolCall -> new ConfirmResult(approved, toolCall))
                .toList();

        Msg resumeMsg = UserMessage.builder()
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, results))
                .build();
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .userId(tenant.tenantId())
                .sessionId(conversationId)
                .build();

        StringJoiner answer = new StringJoiner("");
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Boolean> awaitingConfirm = new AtomicReference<>(false);
        try {
            // 空正文 + 确认结果元数据，框架会恢复挂起的工具调用
            agent.streamEvents(List.of(resumeMsg), runtimeContext)
                    .doOnNext(scopeEvent -> {
                        if (scopeEvent instanceof RequireUserConfirmEvent confirmEvent) {
                            events.accept(mapConfirm(tenant.tenantId(), resumeTaskId, conversationId, confirmEvent));
                            awaitingConfirm.set(true);
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
            events.accept(new AgentEvent("error", error.get().getMessage()));
            events.accept(new AgentEvent("done", "error"));
            throw new IllegalStateException("AgentScope resume failed", error.get());
        }
        if (Boolean.TRUE.equals(awaitingConfirm.get())) {
            try {
                agent.saveAgentState(tenant.tenantId(), conversationId);
            } catch (Exception ignored) {
            }
            return;
        }
        events.accept(new AgentEvent("done", "ok"));
        stateSaver.accept("{\"last\":\"" + escape(answer.toString()) + "\",\"resumed\":true}");
        try {
            agent.saveAgentState(tenant.tenantId(), conversationId);
        } catch (Exception ignored) {
        }
    }

    /**
     * 从上下文末条 assistant 消息中收集 ASKING 工具调用。
     */
    private static List<ToolUseBlock> askingToolCalls(List<Msg> context) {
        if (context == null || context.isEmpty()) {
            return List.of();
        }
        for (int i = context.size() - 1; i >= 0; i--) {
            Msg msg = context.get(i);
            if (msg.getRole() != MsgRole.ASSISTANT) {
                continue;
            }
            if (msg.getContent() == null) {
                return List.of();
            }
            return msg.getContent().stream()
                    .filter(ToolUseBlock.class::isInstance)
                    .map(ToolUseBlock.class::cast)
                    .filter(toolCall -> toolCall.getState() == ToolCallState.ASKING)
                    .toList();
        }
        return List.of();
    }

    private AgentEvent mapConfirm(String tenantId, String taskId, String conversationId,
                                  RequireUserConfirmEvent event) {
        List<ConfirmPayloadFactory.ToolCall> tools = new ArrayList<>();
        List<ToolUseBlock> calls = event.getToolCalls() == null ? List.of() : event.getToolCalls();
        for (ToolUseBlock call : calls) {
            tools.add(new ConfirmPayloadFactory.ToolCall(call.getName(), toJson(call.getInput())));
        }
        if (tools.isEmpty()) {
            tools.add(new ConfirmPayloadFactory.ToolCall("unknown", "{}"));
        }
        String data = ConfirmPayloadFactory.suspendAndSerialize(
                confirmGate, tenantId, taskId, conversationId, event.getReplyId(), tools);
        return new AgentEvent("confirm", data);
    }

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
     * {@inheritDoc}
     * <p>对缓存中的 ReActAgent 调用 interrupt，打断进行中的 streamEvents。
     */
    @Override
    public Runnable interruptAction(String tenantId, String conversationId) {
        return () -> agentProvider.getIfPresent(tenantId).ifPresent(agent -> {
            try {
                // 按租户+会话打断，与 RuntimeContext 中的 userId/sessionId 对齐
                agent.interrupt(tenantId, conversationId);
            } catch (Exception ignored) {
                // 取消路径不能因 interrupt 异常而失败，闸门仍需释放
            }
        });
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "'").replace("\n", "\\n");
    }
}
