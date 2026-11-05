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

/**
 * 租户聊天运行编排服务。
 * <p>所属模块：runtime。占并发槽、解析工具目录并调用执行器，成功后记用量与审计。
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
    private final Map<String, String> taskOwners = new ConcurrentHashMap<>();

    /**
     * 注入一轮聊天所需的闸门、目录、状态与观测依赖。
     *
     * @param runGate 租户并发闸门
     * @param toolCatalog 租户工具目录
     * @param stateStore 会话状态存储
     * @param agentExecutor 具体执行器（echo 或 AgentScope）
     * @param fingerprintTracker 目录指纹跟踪，用于感知工具集变化
     * @param auditService 审计服务
     * @param usageService 用量服务
     */
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

    /**
     * 启动一轮聊天：占并发槽、解析目录、执行 Agent，结束后释放槽位。
     *
     * @param tenant 当前租户上下文
     * @param conversationId 会话 ID
     * @param message 用户消息
     * @param maxConcurrent 该租户允许的最大并发
     * @return 任务 ID 与本轮已缓冲事件
     * @throws IllegalStateException 并发已满，未能占用槽位
     */
    public ChatRun start(TenantContext tenant, String conversationId, String message, int maxConcurrent) {
        String taskId = "task_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        // 先占槽再执行，避免无限并发打满模型与 MCP
        RunGate.AcquireResult acquired = runGate.tryAcquire(tenant.tenantId(), taskId, maxConcurrent);
        if (!acquired.acquired()) {
            throw new IllegalStateException(acquired.reason());
        }
        // 记录任务归属，取消时才能校验请求方是否为同一租户
        taskOwners.put(taskId, tenant.tenantId());
        ResolvedCatalog catalog = toolCatalog.resolve(tenant.tenantId());
        // 目录指纹变化时记一次重建，避免后续仍按旧工具集理解本轮
        fingerprintTracker.getOrRebuild(tenant.tenantId(), catalog);
        String prior = stateStore.load(tenant.tenantId(), conversationId).orElse("{}");
        List<AgentEvent> buffer = new ArrayList<>();
        try {
            // 传入 taskId，执行器挂起确认单时才能与 /v1/confirms 对齐
            agentExecutor.run(tenant, taskId, conversationId, message, catalog, prior, buffer::add, payload ->
                    stateStore.save(tenant.tenantId(), conversationId, payload));
            // 仅整轮成功才记用量与完成审计；执行器抛错会跳过这两步
            usageService.record(tenant.tenantId(), taskId, "runs", 1);
            auditService.record(tenant.tenantId(), "CHAT_COMPLETE", taskId, "{}");
        } finally {
            // 成功或失败都归还并发槽，避免租户额度泄漏
            runGate.release(tenant.tenantId(), taskId);
        }
        return new ChatRun(taskId, List.copyOf(buffer));
    }

    /**
     * 取消指定任务。仅任务所属租户可以取消。
     *
     * @param requesterTenantId 请求方租户 ID
     * @param taskId 任务 ID
     * @throws com.agent.gateway.runtime.NotFoundException 任务不存在或不属于该租户
     */
    public void cancel(String requesterTenantId, String taskId) {
        String owner = taskOwners.get(taskId);
        if (owner == null || !owner.equals(requesterTenantId)) {
            // 未知任务与跨租户统一按未找到处理，避免泄露任务是否存在
            throw new com.agent.gateway.runtime.NotFoundException("task not found");
        }
        // 取消即释放槽位，同租户后续请求才能再次进入
        runGate.release(owner, taskId);
        auditService.record(owner, "CHAT_CANCEL", taskId, "{}");
        taskOwners.remove(taskId);
    }

    /**
     * 一轮聊天的运行结果。
     * <p>所属模块：runtime。携带任务 ID 与已缓冲的 Agent 事件。
     *
     * @param taskId 任务 ID
     * @param events 本轮产生的事件
     */
    public record ChatRun(String taskId, List<AgentEvent> events) {
    }
}
