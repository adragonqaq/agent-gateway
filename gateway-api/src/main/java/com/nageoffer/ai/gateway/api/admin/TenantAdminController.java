package com.nageoffer.ai.gateway.api.admin;

import com.nageoffer.ai.gateway.api.auth.TenantContextHolder;
import com.nageoffer.ai.gateway.tenant.IssuedApiKey;
import com.nageoffer.ai.gateway.tenant.TenantAdminService;
import com.nageoffer.ai.gateway.tenant.TenantContext;
import com.nageoffer.ai.gateway.tenant.TenantRecord;
import com.nageoffer.ai.gateway.tenant.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/tenants")
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;

    public TenantAdminController(TenantAdminService tenantAdminService) {
        this.tenantAdminService = tenantAdminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTenant(@RequestBody CreateTenantRequest request) {
        requireAdmin();
        TenantRecord tenant = tenantAdminService.createTenant(
                request.name(),
                request.modelTier(),
                request.credentialBinding(),
                request.maxConcurrent() == null ? 5 : request.maxConcurrent());
        return Map.of(
                "id", tenant.id(),
                "name", tenant.name(),
                "modelTier", tenant.modelTier(),
                "maxConcurrent", tenant.maxConcurrent());
    }

    @PostMapping("/{tenantId}/api-keys")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> issueKey(
            @PathVariable String tenantId,
            @RequestBody(required = false) IssueApiKeyRequest request) {
        requireAdmin();
        String role = request == null ? "RUNTIME" : request.role();
        IssuedApiKey issued = tenantAdminService.issueApiKey(tenantId, role);
        return Map.of(
                "id", issued.id(),
                "apiKey", issued.rawKey(),
                "prefix", issued.prefix(),
                "role", issued.role());
    }

    private void requireAdmin() {
        TenantContext ctx = TenantContextHolder.require();
        if (!"ADMIN".equalsIgnoreCase(ctx.role())) {
            throw new UnauthorizedException("Admin role required");
        }
    }
}
