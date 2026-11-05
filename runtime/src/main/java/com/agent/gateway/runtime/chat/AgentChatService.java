package com.agent.gateway.runtime.chat;

import com.agent.gateway.memory.SessionStateStore;
import com.agent.gateway.observe.AuditService;
import com.agent.gateway.observe.UsageService;
import com.agent.gateway.runtime.ConfirmDecision;
import com.agent.gateway.runtime.ConfirmGate;
import com.agent.gateway.runtime.PendingConfirmation;
import com.agent.gateway.runtime.RunGate;
import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tools.ResolvedCatalog;
import com.agent.gateway.tools.TenantToolCatalog;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 租户聊天运行编排服务。
 * <p>所属模块：runtime。占并发槽、解析工具目录并调用执行器；支持边跑边推事件与确认续跑。
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
    private final Map<String, String> taskOwners = new ConcurrentHashMap<>();

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
            ConfirmGate confirmGate) {
        this.runGate = runGate;
        this.toolCatalog = toolCatalog;
        this.stateStore = stateStore;
        this.agentExecutor = agentExecutor;
        this.fingerprintTracker = fingerprintTracker;
        this.auditService = auditService;
        this.usageService = usageService;
        this.confirmGate = confirmGate;
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
     * <p>占用槽位成功后立刻回调 {@code onTaskReady}，便于客户端先收到 meta.taskId；
     * 调用方应在独立线程中调用，以便 HTTP 线程立刻返回 {@code SseEmitter}。
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
        taskOwners.put(taskId, tenant.tenantId());
        // 先告知 taskId，客户端才能在流中途发起 cancel
        onTaskReady.accept(taskId);
        ResolvedCatalog catalog = toolCatalog.resolve(tenant.tenantId());
        fingerprintTracker.getOrRebuild(tenant.tenantId(), catalog);
        String prior = stateStore.load(tenant.tenantId(), conversationId).orElse("{}");
        AtomicBoolean awaitingConfirm = new AtomicBoolean(false);
        try {
            agentExecutor.run(tenant, taskId, conversationId, message, catalog, prior, event -> {
                if ("confirm".equals(event.type())) {
                    awaitingConfirm.set(true);
                }
                // 立即下推，不等整轮结束
                sink.accept(event);
            }, payload -> stateStore.save(tenant.tenantId(), conversationId, payload));
            if (awaitingConfirm.get()) {
                auditService.record(tenant.tenantId(), "CHAT_AWAITING_CONFIRM", taskId, "{}");
            } else {
                usageService.record(tenant.tenantId(), taskId, "runs", 1);
                auditService.record(tenant.tenantId(), "CHAT_COMPLETE", taskId, "{}");
            }
        } finally {
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
     *
     * @param tenant 租户上下文
     * @param confirmId 确认单 ID
     * @param approved 是否批准
     * @param maxConcurrent 续跑并发上限
     * @param onTaskReady 续跑 taskId 就绪回调
     * @param sink 事件消费者
     * @return 续跑 taskId
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
            throw new IllegalStateException("confirmation expired");
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
        taskOwners.put(resumeTaskId, tenant.tenantId());
        onTaskReady.accept(resumeTaskId);
        ResolvedCatalog catalog = toolCatalog.resolve(tenant.tenantId());
        String conversationId = anchor.conversationId() == null ? "" : anchor.conversationId();
        AtomicBoolean awaitingConfirm = new AtomicBoolean(false);
        try {
            agentExecutor.resumeConfirm(tenant, resumeTaskId, settled, approved, catalog, event -> {
                if ("confirm".equals(event.type())) {
                    awaitingConfirm.set(true);
                }
                sink.accept(event);
            }, payload -> {
                if (!conversationId.isBlank()) {
                    stateStore.save(tenant.tenantId(), conversationId, payload);
                }
            });
            if (!awaitingConfirm.get()) {
                usageService.record(tenant.tenantId(), resumeTaskId, "runs", 1);
                auditService.record(tenant.tenantId(), "CHAT_CONFIRM_RESUME_COMPLETE", resumeTaskId, "{}");
            }
        } finally {
            runGate.release(tenant.tenantId(), resumeTaskId);
        }
        return resumeTaskId;
    }

    /**
     * 取消指定任务。仅任务所属租户可以取消。
     */
    public void cancel(String requesterTenantId, String taskId) {
        String owner = taskOwners.get(taskId);
        if (owner == null || !owner.equals(requesterTenantId)) {
            throw new com.agent.gateway.runtime.NotFoundException("task not found");
        }
        runGate.release(owner, taskId);
        auditService.record(owner, "CHAT_CANCEL", taskId, "{}");
        taskOwners.remove(taskId);
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
