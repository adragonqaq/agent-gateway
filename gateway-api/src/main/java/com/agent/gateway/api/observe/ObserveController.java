package com.agent.gateway.api.observe;

import com.agent.gateway.api.auth.TenantContextHolder;
import com.agent.gateway.observe.AuditService;
import com.agent.gateway.observe.QuotaService;
import com.agent.gateway.observe.TraceQueryService;
import com.agent.gateway.observe.UsageService;
import com.agent.gateway.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 观测查询 API（审计 / 用量 / Trace）。
 * <p>所属模块：gateway-api。仅返回当前鉴权租户数据，禁止通过参数指定其他 tenantId。
 * member 强制 {@code scope=me}；owner / API Key 可看 {@code project}。
 */
@RestController
@RequestMapping("/v1/observe")
public class ObserveController {

    private final AuditService auditService;
    private final UsageService usageService;
    private final QuotaService quotaService;
    private final TraceQueryService traceQueryService;

    /**
     * @param auditService 审计服务
     * @param usageService 用量服务
     * @param quotaService 配额
     * @param traceQueryService Trace 查询
     */
    public ObserveController(
            AuditService auditService,
            UsageService usageService,
            QuotaService quotaService,
            TraceQueryService traceQueryService) {
        this.auditService = auditService;
        this.usageService = usageService;
        this.quotaService = quotaService;
        this.traceQueryService = traceQueryService;
    }

    /**
     * 查询本租户最近审计事件。
     *
     * @param limit 条数
     * @param scope {@code me} | {@code project}；member 强制 me
     */
    @GetMapping("/audit")
    public List<Map<String, Object>> listAudit(
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "scope", required = false) String scope) {
        TenantContext tenant = TenantContextHolder.require();
        return auditService.listRecent(tenant.tenantId(), resolveUserFilter(tenant, scope), limit);
    }

    /**
     * 汇总本租户各指标用量。
     *
     * @param scope {@code me} | {@code project}
     */
    @GetMapping("/usage")
    public List<Map<String, Object>> usageSummary(
            @RequestParam(name = "scope", required = false) String scope) {
        TenantContext tenant = TenantContextHolder.require();
        return usageService.summarize(tenant.tenantId(), resolveUserFilter(tenant, scope));
    }

    /**
     * 本租户 token 配额快照（quota / used / remaining / myTokenUsed）。
     */
    @GetMapping("/quota")
    public Map<String, Object> quotaSnapshot() {
        TenantContext tenant = TenantContextHolder.require();
        return quotaService.snapshot(tenant.tenantId(), tenant.userId());
    }

    /**
     * 按任务汇总用量（对话台回填本轮 token）。
     *
     * @param taskId 任务 ID
     * @return metric → total
     */
    @GetMapping("/usage/tasks/{taskId}")
    public Map<String, Long> usageForTask(@PathVariable String taskId) {
        TenantContext tenant = TenantContextHolder.require();
        return usageService.summarizeForTask(tenant.tenantId(), taskId);
    }

    /**
     * 按 traceId 查询 span 树（本租户）。
     *
     * @param traceId 链路 ID
     * @return spans + tree
     */
    @GetMapping("/traces/{traceId}")
    public Map<String, Object> getTrace(@PathVariable String traceId) {
        TenantContext tenant = TenantContextHolder.require();
        return traceQueryService.getTrace(tenant.tenantId(), traceId);
    }

    /**
     * 按会话列出最近 trace 摘要。
     *
     * @param conversationId 会话 ID
     * @param limit 条数
     * @return 摘要列表
     */
    @GetMapping("/traces")
    public List<Map<String, Object>> listTraces(
            @RequestParam("conversationId") String conversationId,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        TenantContext tenant = TenantContextHolder.require();
        return traceQueryService.listByConversation(tenant.tenantId(), conversationId, limit);
    }

    /**
     * member 强制只看自己；owner/API Key 默认 project，可显式 me。
     */
    private static String resolveUserFilter(TenantContext tenant, String scope) {
        boolean meRequested = scope != null && "me".equalsIgnoreCase(scope.trim());
        boolean projectRequested = scope != null && "project".equalsIgnoreCase(scope.trim());
        if (tenant.isJwt() && !tenant.isProjectOwner()) {
            return tenant.userId();
        }
        if (meRequested) {
            return tenant.userId();
        }
        if (projectRequested || scope == null || scope.isBlank()) {
            return null;
        }
        // 未知 scope 按 me 兜底（有 user 时），避免误扩权
        return tenant.userId();
    }
}
