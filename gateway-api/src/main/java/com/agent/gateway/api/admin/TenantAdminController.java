package com.agent.gateway.api.admin;

import com.agent.gateway.api.auth.TenantContextHolder;
import com.agent.gateway.observe.QuotaService;
import com.agent.gateway.tenant.IssuedApiKey;
import com.agent.gateway.tenant.TenantAdminService;
import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tenant.TenantRecord;
import com.agent.gateway.tenant.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 租户与 API Key 管理面 API。
 * 仅 ADMIN 角色可调用，用于开户与签发密钥。
 */
@RestController
@RequestMapping("/admin/tenants")
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;
    private final QuotaService quotaService;

    /**
     * @param tenantAdminService 租户管理服务
     * @param quotaService 配额服务
     */
    public TenantAdminController(TenantAdminService tenantAdminService, QuotaService quotaService) {
        this.tenantAdminService = tenantAdminService;
        this.quotaService = quotaService;
    }

    /**
     * 创建新租户。
     *
     * @param request 创建租户请求体
     * @return 新建租户摘要（不含密钥）
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTenant(@RequestBody CreateTenantRequest request) {
        // requireAdmin：开户属于高权限操作，禁止 RUNTIME Key 调用
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

    /**
     * 为指定租户签发 API Key（明文仅此响应返回一次）。
     *
     * @param tenantId 目标租户 ID
     * @param request 签发请求；可为空，默认 RUNTIME
     * @return 含明文 apiKey 的签发结果
     */
    @PostMapping("/{tenantId}/api-keys")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> issueKey(
            @PathVariable String tenantId,
            @RequestBody(required = false) IssueApiKeyRequest request) {
        // requireAdmin：签发密钥等同授权凭证分发，必须限制为 ADMIN
        requireAdmin();
        String role = request == null ? "RUNTIME" : request.role();
        IssuedApiKey issued = tenantAdminService.issueApiKey(tenantId, role);
        return Map.of(
                "id", issued.id(),
                "apiKey", issued.rawKey(),
                "prefix", issued.prefix(),
                "role", issued.role());
    }

    /**
     * 设置租户累计 token 配额；{@code tokenQuota=null} 表示不限额。
     *
     * @param tenantId 目标租户
     * @param body 请求体，含 tokenQuota
     * @return 配额快照
     */
    @PutMapping("/{tenantId}/quota")
    public Map<String, Object> setQuota(
            @PathVariable String tenantId,
            @RequestBody Map<String, Object> body) {
        requireAdmin();
        Object raw = body == null ? null : body.get("tokenQuota");
        Long quota = null;
        if (raw instanceof Number n) {
            quota = n.longValue();
        } else if (raw != null && !String.valueOf(raw).isBlank() && !"null".equalsIgnoreCase(String.valueOf(raw))) {
            quota = Long.parseLong(String.valueOf(raw));
        }
        if (quota != null && quota < 0) {
            throw new IllegalArgumentException("tokenQuota must be >= 0 or null");
        }
        quotaService.setTokenQuota(tenantId, quota);
        return quotaService.snapshot(tenantId);
    }

    /**
     * 从请求线程上下文取出租户并校验 ADMIN 角色。
     */
    private void requireAdmin() {
        // TenantContextHolder：读取过滤器绑定的调用方身份，再校验是否为 ADMIN
        TenantContext ctx = TenantContextHolder.require();
        if (!"ADMIN".equalsIgnoreCase(ctx.role())) {
            throw new UnauthorizedException("Admin role required");
        }
    }
}
