package com.agent.gateway.api.chat;

import com.agent.gateway.api.auth.TenantContextHolder;
import com.agent.gateway.observe.AuditService;
import com.agent.gateway.runtime.ConfirmGate;
import com.agent.gateway.runtime.PendingConfirmation;
import com.agent.gateway.tenant.TenantContext;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 人工确认门禁 API。
 * 对需要确认的工具调用执行批准或拒绝，并写入审计。
 */
@RestController
@RequestMapping("/v1/confirms")
public class ConfirmController {

    private final ConfirmGate confirmGate;
    private final AuditService auditService;

    /**
     * @param confirmGate 确认门禁
     * @param auditService 审计服务
     */
    public ConfirmController(ConfirmGate confirmGate, AuditService auditService) {
        this.confirmGate = confirmGate;
        this.auditService = auditService;
    }

    /**
     * 批准待确认的工具调用，使其可以继续执行。
     *
     * @param confirmId 确认单 ID
     * @return 确认单 ID 与最新状态
     */
    @PostMapping("/{confirmId}/approve")
    public Map<String, Object> approve(@PathVariable String confirmId) {
        // TenantContextHolder：批准必须绑定租户，防止跨租户放行他人确认单
        TenantContext tenant = TenantContextHolder.require();
        // confirm approve：放行门禁中的待确认调用，运行时才会真正执行工具
        PendingConfirmation result = confirmGate.confirm(tenant.tenantId(), confirmId);
        auditService.record(tenant.tenantId(), "CONFIRM_APPROVE", confirmId, "{}");
        return Map.of("id", result.id(), "status", result.status().name());
    }

    /**
     * 拒绝待确认的工具调用，阻止其执行。
     *
     * @param confirmId 确认单 ID
     * @return 确认单 ID 与最新状态
     */
    @PostMapping("/{confirmId}/deny")
    public Map<String, Object> deny(@PathVariable String confirmId) {
        // TenantContextHolder：拒绝同样需租户隔离，避免误拒其他租户确认单
        TenantContext tenant = TenantContextHolder.require();
        // confirm deny：关闭门禁并标记拒绝，运行时不得继续执行该工具调用
        PendingConfirmation result = confirmGate.deny(tenant.tenantId(), confirmId);
        auditService.record(tenant.tenantId(), "CONFIRM_DENY", confirmId, "{}");
        return Map.of("id", result.id(), "status", result.status().name());
    }
}
