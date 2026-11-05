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

/**
 * 租户聊天运行编排服务。
 * <p>所属模块：runtime。占并发槽、解析工具目录并调用执行器；支持确认后续跑。
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
     * 启动一轮聊天：占并发槽、解析目录、执行 Agent，结束后释放槽位。
     */
    public ChatRun start(TenantContext tenant, String conversationId, String message, int maxConcurrent) {
        String taskId = "task_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        RunGate.AcquireResult acquired = runGate.tryAcquire(tenant.tenantId(), taskId, maxConcurrent);
        if (!acquired.acquired()) {
            throw new IllegalStateException(acquired.reason());
        }
        taskOwners.put(taskId, tenant.tenantId());
        ResolvedCatalog catalog = toolCatalog.resolve(tenant.tenantId());
        fingerprintTracker.getOrRebuild(tenant.tenantId(), catalog);
        String prior = stateStore.load(tenant.tenantId(), conversationId).orElse("{}");
        List<AgentEvent> buffer = new ArrayList<>();
        try {
            agentExecutor.run(tenant, taskId, conversationId, message, catalog, prior, buffer::add, payload ->
                    stateStore.save(tenant.tenantId(), conversationId, payload));
            boolean awaiting = buffer.stream().anyMatch(e -> "confirm".equals(e.type()));
            if (!awaiting) {
                usageService.record(tenant.tenantId(), taskId, "runs", 1);
                auditService.record(tenant.tenantId(), "CHAT_COMPLETE", taskId, "{}");
            } else {
                // 挂起确认时保留任务归属，便于审计；并发槽仍释放，避免卡死额度
                auditService.record(tenant.tenantId(), "CHAT_AWAITING_CONFIRM", taskId, "{}");
            }
        } finally {
            runGate.release(tenant.tenantId(), taskId);
        }
        return new ChatRun(taskId, List.copyOf(buffer));
    }

    /**
     * 对确认单做批准或拒绝，并续跑 Agent。
     * <p>同一 taskId 下其余 PENDING 确认单会一并结算，避免半放行。
     *
     * @param tenant 租户上下文
     * @param confirmId 确认单 ID
     * @param approved true=批准，false=拒绝
     * @param maxConcurrent 续跑占用的并发上限
     * @return 续跑任务与事件
     */
    public ChatRun resumeAfterConfirm(TenantContext tenant, String confirmId, boolean approved, int maxConcurrent) {
        PendingConfirmation anchor = confirmGate.get(tenant.tenantId(), confirmId);
        if (anchor.status() == ConfirmDecision.EXPIRED) {
            throw new IllegalStateException("confirmation expired");
        }
        ConfirmDecision decision = approved ? ConfirmDecision.CONFIRMED : ConfirmDecision.DENIED;
        // 整卡结算同一 task 下的 PENDING，与前端一张确认卡语义一致
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
        ResolvedCatalog catalog = toolCatalog.resolve(tenant.tenantId());
        List<AgentEvent> buffer = new ArrayList<>();
        String conversationId = anchor.conversationId() == null ? "" : anchor.conversationId();
        try {
            agentExecutor.resumeConfirm(tenant, resumeTaskId, settled, approved, catalog, buffer::add, payload -> {
                if (!conversationId.isBlank()) {
                    stateStore.save(tenant.tenantId(), conversationId, payload);
                }
            });
            boolean awaiting = buffer.stream().anyMatch(e -> "confirm".equals(e.type()));
            if (!awaiting) {
                usageService.record(tenant.tenantId(), resumeTaskId, "runs", 1);
                auditService.record(tenant.tenantId(), "CHAT_CONFIRM_RESUME_COMPLETE", resumeTaskId, "{}");
            }
        } finally {
            runGate.release(tenant.tenantId(), resumeTaskId);
        }
        return new ChatRun(resumeTaskId, List.copyOf(buffer));
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
     * 一轮聊天的运行结果。
     *
     * @param taskId 任务 ID
     * @param events 本轮产生的事件
     */
    public record ChatRun(String taskId, List<AgentEvent> events) {
    }
}
