package com.agent.gateway.runtime.chat;

import com.agent.gateway.memory.SessionStateStore;
import com.agent.gateway.observe.AuditService;
import com.agent.gateway.observe.QuotaService;
import com.agent.gateway.observe.SpanKind;
import com.agent.gateway.observe.SpanRecord;
import com.agent.gateway.observe.SpanRecorder;
import com.agent.gateway.observe.SpanStatus;
import com.agent.gateway.observe.TraceContext;
import com.agent.gateway.observe.TraceContextHolder;
import com.agent.gateway.observe.TraceIds;
import com.agent.gateway.observe.TraceQueryService;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 租户聊天运行编排服务。
 * <p>所属模块：runtime。占并发槽、解析工具目录并调用执行器；支持边跑边推、确认续跑与真正打断取消。
 * 每轮生成 {@code traceId} 并写入 RUN/TOOL/CONFIRM/LLM span。
 */
@Service
public class AgentChatService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RunGate runGate;
    private final TenantToolCatalog toolCatalog;
    private final SessionStateStore stateStore;
    private final AgentExecutor agentExecutor;
    private final CatalogFingerprintTracker fingerprintTracker;
    private final AuditService auditService;
    private final UsageService usageService;
    private final QuotaService quotaService;
    private final ConfirmGate confirmGate;
    private final ActiveRunRegistry activeRuns;
    private final SpanRecorder spanRecorder;
    private final TraceQueryService traceQueryService;

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
            QuotaService quotaService,
            ConfirmGate confirmGate,
            ActiveRunRegistry activeRuns,
            SpanRecorder spanRecorder,
            TraceQueryService traceQueryService) {
        this.runGate = runGate;
        this.toolCatalog = toolCatalog;
        this.stateStore = stateStore;
        this.agentExecutor = agentExecutor;
        this.fingerprintTracker = fingerprintTracker;
        this.auditService = auditService;
        this.usageService = usageService;
        this.quotaService = quotaService;
        this.confirmGate = confirmGate;
        this.activeRuns = activeRuns;
        this.spanRecorder = spanRecorder;
        this.traceQueryService = traceQueryService;
    }

    /**
     * 同步缓冲一整轮事件（便于单测断言）。
     */
    public ChatRun start(TenantContext tenant, String conversationId, String message, int maxConcurrent) {
        List<AgentEvent> buffer = new ArrayList<>();
        String[] ids = new String[2];
        startStreaming(tenant, conversationId, message, maxConcurrent, ready -> {
            ids[0] = ready.taskId();
            ids[1] = ready.traceId();
        }, buffer::add);
        return new ChatRun(ids[0], ids[1], List.copyOf(buffer));
    }

    /**
     * 边执行边把事件推给 {@code sink}，适合 SSE 流式输出。
     *
     * @return 本轮 taskId
     */
    public String startStreaming(
            TenantContext tenant,
            String conversationId,
            String message,
            int maxConcurrent,
            Consumer<RunReady> onReady,
            Consumer<AgentEvent> sink) {
        quotaService.requireTokenQuotaAvailable(tenant.tenantId());
        String taskId = "task_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String traceId = TraceIds.newTraceId();
        RunGate.AcquireResult acquired = runGate.tryAcquire(tenant.tenantId(), taskId, maxConcurrent);
        if (!acquired.acquired()) {
            throw new IllegalStateException(acquired.reason());
        }
        String runSpanId = spanRecorder.start(
                tenant.tenantId(),
                traceId,
                null,
                conversationId,
                taskId,
                "chat.run",
                SpanKind.RUN,
                SpanStatus.OK,
                null);
        TraceContext ctx = new TraceContext(tenant.tenantId(), conversationId, traceId, taskId, runSpanId);
        TraceContextHolder.set(ctx);
        EventSpanBridge bridge = new EventSpanBridge(spanRecorder, ctx);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        activeRuns.register(
                taskId,
                tenant.tenantId(),
                conversationId,
                cancelled,
                agentExecutor.interruptAction(tenant.tenantId(), conversationId));
        onReady.accept(new RunReady(taskId, traceId));
        ResolvedCatalog catalog = toolCatalog.resolve(tenant.tenantId());
        fingerprintTracker.getOrRebuild(tenant.tenantId(), catalog);
        String prior = stateStore.load(tenant.tenantId(), conversationId).orElse("{}");
        AtomicBoolean awaitingConfirm = new AtomicBoolean(false);
        AtomicReference<String> lastDonePayload = new AtomicReference<>();
        Consumer<AgentEvent> tracedSink = bridge.wrap(event -> {
            if (cancelled.get()) {
                return;
            }
            if ("confirm".equals(event.type())) {
                awaitingConfirm.set(true);
            }
            if ("done".equals(event.type())) {
                lastDonePayload.set(event.data());
            }
            sink.accept(event);
        });
        try {
            try {
                agentExecutor.run(
                        tenant,
                        taskId,
                        conversationId,
                        message,
                        catalog,
                        prior,
                        tracedSink,
                        payload -> stateStore.save(tenant.tenantId(), conversationId, payload));
            } catch (RuntimeException ex) {
                if (!cancelled.get()) {
                    bridge.finish(SpanStatus.ERROR);
                    spanRecorder.fail(tenant.tenantId(), runSpanId, "{\"error\":true}");
                    auditService.record(tenant.tenantId(), "CHAT_ERROR", taskId, detail(traceId));
                    throw ex;
                }
            }
            if (cancelled.get()) {
                sink.accept(new AgentEvent("cancel", "cancelled"));
                bridge.finish(SpanStatus.CANCELLED);
                spanRecorder.cancel(tenant.tenantId(), runSpanId);
                auditService.record(tenant.tenantId(), "CHAT_CANCEL", taskId, detail(traceId));
            } else if (awaitingConfirm.get()) {
                bridge.finish(SpanStatus.WAITING);
                spanRecorder.end(tenant.tenantId(), runSpanId, SpanStatus.WAITING, null);
                auditService.record(tenant.tenantId(), "CHAT_AWAITING_CONFIRM", taskId, detail(traceId));
            } else {
                bridge.finish(SpanStatus.OK);
                spanRecorder.ok(tenant.tenantId(), runSpanId);
                recordRunUsage(tenant.tenantId(), taskId, lastDonePayload.get());
                auditService.record(tenant.tenantId(), "CHAT_COMPLETE", taskId, detail(traceId));
            }
        } finally {
            activeRuns.remove(taskId);
            runGate.release(tenant.tenantId(), taskId);
            TraceContextHolder.clear();
        }
        return taskId;
    }

    /**
     * 同步缓冲确认续跑事件（便于单测）。
     */
    public ChatRun resumeAfterConfirm(TenantContext tenant, String confirmId, boolean approved, int maxConcurrent) {
        List<AgentEvent> buffer = new ArrayList<>();
        String[] ids = new String[2];
        resumeAfterConfirmStreaming(tenant, confirmId, approved, maxConcurrent, ready -> {
            ids[0] = ready.taskId();
            ids[1] = ready.traceId();
        }, buffer::add);
        return new ChatRun(ids[0], ids[1], List.copyOf(buffer));
    }

    /**
     * 结算确认单后边续跑边推事件。
     */
    public String resumeAfterConfirmStreaming(
            TenantContext tenant,
            String confirmId,
            boolean approved,
            int maxConcurrent,
            Consumer<RunReady> onReady,
            Consumer<AgentEvent> sink) {
        PendingConfirmation anchor = confirmGate.get(tenant.tenantId(), confirmId);
        if (anchor.status() == ConfirmDecision.EXPIRED) {
            auditService.record(tenant.tenantId(), "CONFIRM_EXPIRED", confirmId,
                    "{\"taskId\":\"" + anchor.taskId() + "\",\"source\":\"resume\",\"traceId\":\""
                            + nullToEmpty(anchor.traceId()) + "\"}");
            throw new ExpiredConfirmationException("confirmation expired");
        }
        String traceId = (anchor.traceId() == null || anchor.traceId().isBlank())
                ? TraceIds.newTraceId()
                : anchor.traceId();
        ConfirmDecision decision = approved ? ConfirmDecision.CONFIRMED : ConfirmDecision.DENIED;
        List<PendingConfirmation> settled = confirmGate.decideAllPendingForTask(
                tenant.tenantId(), anchor.taskId(), decision);
        auditService.record(tenant.tenantId(),
                approved ? "CONFIRM_APPROVE" : "CONFIRM_DENY",
                confirmId,
                "{\"taskId\":\"" + anchor.taskId() + "\",\"traceId\":\"" + traceId + "\"}");

        if (approved) {
            quotaService.requireTokenQuotaAvailable(tenant.tenantId());
        }

        String resumeTaskId = "task_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        RunGate.AcquireResult acquired = runGate.tryAcquire(tenant.tenantId(), resumeTaskId, maxConcurrent);
        if (!acquired.acquired()) {
            throw new IllegalStateException(acquired.reason());
        }
        String conversationId = anchor.conversationId() == null ? "" : anchor.conversationId();
        String parentRunSpanId = findRootRunSpanId(tenant.tenantId(), traceId);
        String runSpanId = spanRecorder.start(
                tenant.tenantId(),
                traceId,
                parentRunSpanId,
                conversationId,
                resumeTaskId,
                "chat.resume",
                SpanKind.RUN,
                SpanStatus.OK,
                "{\"approved\":" + approved + "}");
        TraceContext ctx = new TraceContext(tenant.tenantId(), conversationId, traceId, resumeTaskId, runSpanId);
        TraceContextHolder.set(ctx);
        EventSpanBridge bridge = new EventSpanBridge(spanRecorder, ctx);
        // 原 CONFIRM span 可能已在首轮留下 WAITING；续跑侧用 attrs 记决策，并记一条短 confirm settle
        String settleSpan = spanRecorder.start(
                tenant.tenantId(),
                traceId,
                runSpanId,
                conversationId,
                resumeTaskId,
                "confirm.settle",
                SpanKind.CONFIRM,
                SpanStatus.OK,
                "{\"approved\":" + approved + ",\"confirmId\":\"" + confirmId + "\"}");
        spanRecorder.end(tenant.tenantId(), settleSpan, approved ? SpanStatus.OK : SpanStatus.CANCELLED, null);

        AtomicBoolean cancelled = new AtomicBoolean(false);
        activeRuns.register(
                resumeTaskId,
                tenant.tenantId(),
                conversationId,
                cancelled,
                agentExecutor.interruptAction(tenant.tenantId(), conversationId));
        onReady.accept(new RunReady(resumeTaskId, traceId));
        ResolvedCatalog catalog = toolCatalog.resolve(tenant.tenantId());
        AtomicBoolean awaitingConfirm = new AtomicBoolean(false);
        AtomicReference<String> lastDonePayload = new AtomicReference<>();
        Consumer<AgentEvent> tracedSink = bridge.wrap(event -> {
            if (cancelled.get()) {
                return;
            }
            if ("confirm".equals(event.type())) {
                awaitingConfirm.set(true);
            }
            if ("done".equals(event.type())) {
                lastDonePayload.set(event.data());
            }
            sink.accept(event);
        });
        try {
            agentExecutor.resumeConfirm(
                    tenant,
                    resumeTaskId,
                    settled,
                    approved,
                    catalog,
                    tracedSink,
                    payload -> {
                        if (!conversationId.isBlank()) {
                            stateStore.save(tenant.tenantId(), conversationId, payload);
                        }
                    });
            if (cancelled.get()) {
                sink.accept(new AgentEvent("cancel", "cancelled"));
                bridge.finish(SpanStatus.CANCELLED);
                spanRecorder.cancel(tenant.tenantId(), runSpanId);
                auditService.record(tenant.tenantId(), "CHAT_CANCEL", resumeTaskId, detail(traceId));
            } else if (awaitingConfirm.get()) {
                bridge.finish(SpanStatus.WAITING);
                spanRecorder.end(tenant.tenantId(), runSpanId, SpanStatus.WAITING, null);
            } else {
                bridge.finish(SpanStatus.OK);
                spanRecorder.ok(tenant.tenantId(), runSpanId);
                recordRunUsage(tenant.tenantId(), resumeTaskId, lastDonePayload.get());
                auditService.record(tenant.tenantId(), "CHAT_CONFIRM_RESUME_COMPLETE", resumeTaskId, detail(traceId));
            }
        } finally {
            activeRuns.remove(resumeTaskId);
            runGate.release(tenant.tenantId(), resumeTaskId);
            TraceContextHolder.clear();
        }
        return resumeTaskId;
    }

    /**
     * 取消指定任务：触发 Agent interrupt，并释放并发槽。
     */
    public void cancel(String requesterTenantId, String taskId) {
        ActiveRunRegistry.ActiveRun run = activeRuns.find(taskId)
                .orElseThrow(() -> new com.agent.gateway.runtime.NotFoundException("task not found"));
        if (!run.tenantId().equals(requesterTenantId)) {
            throw new com.agent.gateway.runtime.NotFoundException("task not found");
        }
        run.requestCancel();
        String traceId = TraceContextHolder.get() == null ? "" : TraceContextHolder.get().traceId();
        auditService.record(run.tenantId(), "CHAT_CANCEL_REQUESTED", taskId, detail(traceId));
    }

    /**
     * 一轮聊天的缓冲结果（测试/兼容用）。
     *
     * @param taskId 任务 ID
     * @param traceId 链路 ID
     * @param events 本轮事件
     */
    public record ChatRun(String taskId, String traceId, List<AgentEvent> events) {
    }

    /**
     * 记一次 runs，并在 done 带了 token 时写入 prompt/completion/total。
     */
    private void recordRunUsage(String tenantId, String taskId, String donePayload) {
        usageService.record(tenantId, taskId, "runs", 1);
        if (donePayload == null || donePayload.isBlank()) {
            return;
        }
        try {
            JsonNode node = JSON.readTree(donePayload);
            if (!node.hasNonNull("promptTokens") || !node.hasNonNull("completionTokens")) {
                return;
            }
            long prompt = Math.max(0L, node.get("promptTokens").asLong());
            long completion = Math.max(0L, node.get("completionTokens").asLong());
            usageService.record(tenantId, taskId, "prompt_tokens", prompt);
            usageService.record(tenantId, taskId, "completion_tokens", completion);
            usageService.record(tenantId, taskId, "total_tokens", prompt + completion);
        } catch (Exception ignored) {
            // done 解析失败不影响主流程
        }
    }

    private String findRootRunSpanId(String tenantId, String traceId) {
        List<SpanRecord> spans = traceQueryService.listSpans(tenantId, traceId);
        return spans.stream()
                .filter(s -> SpanKind.RUN.name().equals(s.kind()))
                .filter(s -> s.parentSpanId() == null || s.parentSpanId().isBlank())
                .map(SpanRecord::id)
                .findFirst()
                .orElse(null);
    }

    private static String detail(String traceId) {
        return "{\"traceId\":\"" + nullToEmpty(traceId) + "\"}";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
