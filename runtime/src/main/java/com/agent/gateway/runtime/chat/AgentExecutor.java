package com.agent.gateway.runtime.chat;

import com.agent.gateway.runtime.PendingConfirmation;
import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tools.ResolvedCatalog;

import java.util.List;
import java.util.function.Consumer;

/**
 * 一轮聊天的执行器抽象。
 * <p>所属模块：runtime。由 echo 或 AgentScope 实现具体对话，编排层不关心模型细节。
 */
public interface AgentExecutor {

    /**
     * 执行一轮对话，并把事件与会话快照回写给调用方。
     *
     * @param tenant 租户上下文
     * @param taskId 本轮任务 ID，用于 ConfirmGate 关联
     * @param conversationId 会话 ID
     * @param message 用户消息
     * @param catalog 已解析的工具目录
     * @param priorState 上一轮会话状态 JSON
     * @param events 事件消费者
     * @param stateSaver 会话状态保存回调
     */
    void run(TenantContext tenant, String taskId, String conversationId, String message,
             ResolvedCatalog catalog, String priorState,
             Consumer<AgentEvent> events, Consumer<String> stateSaver);

    /**
     * 用户确认/拒绝后继续执行。
     *
     * @param tenant 租户上下文
     * @param resumeTaskId 续跑任务 ID（新任务，用于 RunGate）
     * @param settled 已结算的确认单（至少一张，含 conversationId）
     * @param approved 是否批准
     * @param catalog 当前工具目录
     * @param events 事件消费者
     * @param stateSaver 会话状态保存回调
     */
    void resumeConfirm(TenantContext tenant, String resumeTaskId, List<PendingConfirmation> settled,
                       boolean approved, ResolvedCatalog catalog,
                       Consumer<AgentEvent> events, Consumer<String> stateSaver);
}
