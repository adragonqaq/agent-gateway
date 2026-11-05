package com.agent.gateway.runtime.chat;

import com.agent.gateway.memory.SessionStateStore;
import com.agent.gateway.observe.AuditService;
import com.agent.gateway.observe.UsageService;
import com.agent.gateway.runtime.ConfirmDecision;
import com.agent.gateway.runtime.ConfirmGate;
import com.agent.gateway.runtime.ExpiredConfirmationException;
import com.agent.gateway.runtime.PendingConfirmation;
import com.agent.gateway.runtime.RunGate;
import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tools.ResolvedCatalog;
import com.agent.gateway.tools.TenantToolCatalog;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 租户聊天运行编排服务。
 * <p>所属模块：runtime。占并发槽、解析工具目录并调用执行器；支持边跑边推、确认续跑与真正打断取消。
 */
@Service
public class AgentChatService {

    private final RunGate runGate;
    private final TenantToolCatalog toolCatalog;
    private final SessionStateStore stateStore;
    private final AgentExecutor agentExecutor;
    private final CatalogFingerprintTracker fingerprintTracker;
    private final AuditService auditService;
    private final UsageService usageService;
    private final ConfirmGate confirmGate;
    private final ActiveRunRegistry activeRuns;

    /**
     * 注入一轮聊天所需的闸门、目录、状态与观测依赖。
     */
    public AgentChatService(
            RunGate runGate,
            TenantToolCatalog toolCatalog,
            SessionStateStore stateStore,
            AgentExecutor agentExecutor,
            CatalogFingerprintTracker fingerprintTracker,
            AuditService auditService,
            UsageService usageService,
            ConfirmGate confirmGate,
            ActiveRunRegistry activeRuns) {
        this.runGate = runGate;
        this.toolCatalog = toolCatalog;
        this.stateStore = stateStore;
        this.agentExecutor = agentExecutor;
        this.fingerprintTracker = fingerprintTracker;
        this.auditService = auditService;
        this.usageService = usageService;
        this.confirmGate = confirmGate;
        this.activeRuns = activeRuns;
    }

    /**
     * 同步缓冲一整轮事件（便于单测断言）。
     */
    public ChatRun start(TenantContext tenant, String conversationId, String message, int maxConcurrent) {
        List<AgentEvent> buffer = new ArrayList<>();
        String taskId = startStreaming(tenant, conversationId, message, maxConcurrent, id -> {
        }, buffer::add);
        return new ChatRun(taskId, List.copyOf(buffer));
    }

    /**
     * 边执行边把事件推给 {@code sink}，适合 SSE 流式输出。
     *
     * @param tenant 租户上下文
     * @param conversationId 会话 ID
     * @param message 用户消息
     * @param maxConcurrent 并发上限
     * @param onTaskReady 任务 ID 就绪回调（在执行 Agent 之前）
     * @param sink 事件消费者
     * @return 本轮 taskId
     */
    public String startStreaming(
            TenantContext tenant,
            String conversationId,
            String message,
            int maxConcurrent,
            Consumer<String> onTaskReady,
            Consumer<AgentEvent> sink) {
        String taskId = "task_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        RunGate.AcquireResult acquired = runGate.tryAcquire(tenant.tenantId(), taskId, maxConcurrent);
        if (!acquired.acquired()) {
            throw new IllegalStateException(acquired.reason());
        }
        AtomicBoolean cancelled = new AtomicBoolean(false);
        // 登记活跃任务，cancel 才能找到会话并 interrupt
        activeRuns.register(
                taskId,
                tenant.tenantId(),
                conversationId,
                cancelled,
                agentExecutor.interruptAction(tenant.tenantId(), conversationId));
        onTaskReady.accept(taskId);
        ResolvedCatalog catalog = toolCatalog.resolve(tenant.tenantId());
        fingerprintTracker.getOrRebuild(tenant.tenantId(), catalog);
        String prior = stateStore.load(tenant.tenantId(), conversationId).orElse("{}");
        AtomicBoolean awaitingConfirm = new AtomicBoolean(false);
        try {
            try {
                agentExecutor.run(tenant, taskId, conversationId, message, catalog, prior, event -> {
                    if (cancelled.get()) {
                        // 已取消则丢弃后续事件，避免客户端在 cancel 后再收到 answer
                        return;
                    }
                    if ("confirm".equals(event.type())) {
                        awaitingConfirm.set(true);
                    }
                    sink.accept(event);
                }, payload -> stateStore.save(tenant.tenantId(), conversationId, payload));
            } catch (RuntimeException ex) {
                if (!cancelled.get()) {
                    throw ex;
                }
                // interrupt 后执行器可能抛错：仍按取消收口，不把异常冒泡给 SSE 线程
            }
            if (cancelled.get()) {
                sink.accept(new AgentEvent("cancel", "cancelled"));
                auditService.record(tenant.tenantId(), "CHAT_CANCEL", taskId, "{}");
            } else if (awaitingConfirm.get()) {
                auditService.record(tenant.tenantId(), "CHAT_AWAITING_CONFIRM", taskId, "{}");
            } else {
                usageService.record(tenant.tenantId(), taskId, "runs", 1);
                auditService.record(tenant.tenantId(), "CHAT_COMPLETE", taskId, "{}");
            }
        } finally {
            activeRuns.remove(taskId);
            runGate.release(tenant.tenantId(), taskId);
        }
        return taskId;
    }

    /**
     * 同步缓冲确认续跑事件（便于单测）。
     */
    public ChatRun resumeAfterConfirm(TenantContext tenant, String confirmId, boolean approved, int maxConcurrent) {
        List<AgentEvent> buffer = new ArrayList<>();
        String taskId = resumeAfterConfirmStreaming(tenant, confirmId, approved, maxConcurrent, id -> {
        }, buffer::add);
        return new ChatRun(taskId, List.copyOf(buffer));
    }

    /**
     * 结算确认单后边续跑边推事件。
     */
    public String resumeAfterConfirmStreaming(
            TenantContext tenant,
            String confirmId,
            boolean approved,
            int maxConcurrent,
            Consumer<String> onTaskReady,
            Consumer<AgentEvent> sink) {
        PendingConfirmation anchor = confirmGate.get(tenant.tenantId(), confirmId);
        if (anchor.status() == ConfirmDecision.EXPIRED) {
            // 惰性过期路径也落审计，与定时扫描互补（幂等：多次读已是 EXPIRED 仍记一次请求侧拒绝）
            auditService.record(tenant.tenantId(), "CONFIRM_EXPIRED", confirmId,
                    "{\"taskId\":\"" + anchor.taskId() + "\",\"source\":\"resume\"}");
            throw new ExpiredConfirmationException("confirmation expired");
        }
        ConfirmDecision decision = approved ? ConfirmDecision.CONFIRMED : ConfirmDecision.DENIED;
        List<PendingConfirmation> settled = confirmGate.decideAllPendingForTask(
                tenant.tenantId(), anchor.taskId(), decision);
        auditService.record(tenant.tenantId(),
                approved ? "CONFIRM_APPROVE" : "CONFIRM_DENY",
                confirmId,
                "{\"taskId\":\"" + anchor.taskId() + "\"}");

        String resumeTaskId = "task_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        RunGate.AcquireResult acquired = runGate.tryAcquire(tenant.tenantId(), resumeTaskId, maxConcurrent);
        if (!acquired.acquired()) {
            throw new IllegalStateException(acquired.reason());
        }
        String conversationId = anchor.conversationId() == null ? "" : anchor.conversationId();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        activeRuns.register(
                resumeTaskId,
                tenant.tenantId(),
                conversationId,
                cancelled,
                agentExecutor.interruptAction(tenant.tenantId(), conversationId));
        onTaskReady.accept(resumeTaskId);
        ResolvedCatalog catalog = toolCatalog.resolve(tenant.tenantId());
        AtomicBoolean awaitingConfirm = new AtomicBoolean(false);
        try {
            agentExecutor.resumeConfirm(tenant, resumeTaskId, settled, approved, catalog, event -> {
                if (cancelled.get()) {
                    return;
                }
                if ("confirm".equals(event.type())) {
                    awaitingConfirm.set(true);
                }
                sink.accept(event);
            }, payload -> {
                if (!conversationId.isBlank()) {
                    stateStore.save(tenant.tenantId(), conversationId, payload);
                }
            });
            if (cancelled.get()) {
                sink.accept(new AgentEvent("cancel", "cancelled"));
                auditService.record(tenant.tenantId(), "CHAT_CANCEL", resumeTaskId, "{}");
            } else if (!awaitingConfirm.get()) {
                usageService.record(tenant.tenantId(), resumeTaskId, "runs", 1);
                auditService.record(tenant.tenantId(), "CHAT_CONFIRM_RESUME_COMPLETE", resumeTaskId, "{}");
            }
        } finally {
            activeRuns.remove(resumeTaskId);
            runGate.release(tenant.tenantId(), resumeTaskId);
        }
        return resumeTaskId;
    }

    /**
     * 取消指定任务：触发 Agent interrupt，并释放并发槽。
     * <p>仅任务所属租户可以取消；未知或跨租户统一按未找到处理。
     *
     * @param requesterTenantId 请求方租户 ID
     * @param taskId 任务 ID
     */
    public void cancel(String requesterTenantId, String taskId) {
        ActiveRunRegistry.ActiveRun run = activeRuns.find(taskId)
                .orElseThrow(() -> new com.agent.gateway.runtime.NotFoundException("task not found"));
        if (!run.tenantId().equals(requesterTenantId)) {
            throw new com.agent.gateway.runtime.NotFoundException("task not found");
        }
        // 先 interrupt 上游流，再由执行 finally 释放闸门与登记
        run.requestCancel();
        auditService.record(run.tenantId(), "CHAT_CANCEL_REQUESTED", taskId, "{}");
    }

    /**
     * 一轮聊天的缓冲结果（测试/兼容用）。
     *
     * @param taskId 任务 ID
     * @param events 本轮事件
     */
    public record ChatRun(String taskId, List<AgentEvent> events) {
    }
}
