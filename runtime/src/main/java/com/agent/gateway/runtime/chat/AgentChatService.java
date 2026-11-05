package com.agent.gateway.runtime.chat;

import com.agent.gateway.memory.SessionStateStore;
import com.agent.gateway.observe.AuditService;
import com.agent.gateway.observe.UsageService;
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

@Service
public class AgentChatService {

    private final RunGate runGate;
    private final TenantToolCatalog toolCatalog;
    private final SessionStateStore stateStore;
    private final AgentExecutor agentExecutor;
    private final CatalogFingerprintTracker fingerprintTracker;
    private final AuditService auditService;
    private final UsageService usageService;
    private final Map<String, String> taskOwners = new ConcurrentHashMap<>();

    public AgentChatService(
            RunGate runGate,
            TenantToolCatalog toolCatalog,
            SessionStateStore stateStore,
            AgentExecutor agentExecutor,
            CatalogFingerprintTracker fingerprintTracker,
            AuditService auditService,
            UsageService usageService) {
        this.runGate = runGate;
        this.toolCatalog = toolCatalog;
        this.stateStore = stateStore;
        this.agentExecutor = agentExecutor;
        this.fingerprintTracker = fingerprintTracker;
        this.auditService = auditService;
        this.usageService = usageService;
    }

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
            agentExecutor.run(tenant, conversationId, message, catalog, prior, buffer::add, payload ->
                    stateStore.save(tenant.tenantId(), conversationId, payload));
            usageService.record(tenant.tenantId(), taskId, "runs", 1);
            auditService.record(tenant.tenantId(), "CHAT_COMPLETE", taskId, "{}");
        } finally {
            runGate.release(tenant.tenantId(), taskId);
        }
        return new ChatRun(taskId, List.copyOf(buffer));
    }

    public void cancel(String requesterTenantId, String taskId) {
        String owner = taskOwners.get(taskId);
        if (owner == null || !owner.equals(requesterTenantId)) {
            throw new com.agent.gateway.runtime.NotFoundException("task not found");
        }
        runGate.release(owner, taskId);
        auditService.record(owner, "CHAT_CANCEL", taskId, "{}");
        taskOwners.remove(taskId);
    }

    public record ChatRun(String taskId, List<AgentEvent> events) {
    }
}
