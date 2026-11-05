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

@RestController
@RequestMapping("/v1/confirms")
public class ConfirmController {

    private final ConfirmGate confirmGate;
    private final AuditService auditService;

    public ConfirmController(ConfirmGate confirmGate, AuditService auditService) {
        this.confirmGate = confirmGate;
        this.auditService = auditService;
    }

    @PostMapping("/{confirmId}/approve")
    public Map<String, Object> approve(@PathVariable String confirmId) {
        TenantContext tenant = TenantContextHolder.require();
        PendingConfirmation result = confirmGate.confirm(tenant.tenantId(), confirmId);
        auditService.record(tenant.tenantId(), "CONFIRM_APPROVE", confirmId, "{}");
        return Map.of("id", result.id(), "status", result.status().name());
    }

    @PostMapping("/{confirmId}/deny")
    public Map<String, Object> deny(@PathVariable String confirmId) {
        TenantContext tenant = TenantContextHolder.require();
        PendingConfirmation result = confirmGate.deny(tenant.tenantId(), confirmId);
        auditService.record(tenant.tenantId(), "CONFIRM_DENY", confirmId, "{}");
        return Map.of("id", result.id(), "status", result.status().name());
    }
}
