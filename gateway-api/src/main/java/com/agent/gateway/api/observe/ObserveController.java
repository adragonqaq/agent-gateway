package com.agent.gateway.api.observe;

import com.agent.gateway.api.auth.TenantContextHolder;
import com.agent.gateway.observe.AuditService;
import com.agent.gateway.observe.UsageService;
import com.agent.gateway.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 观测查询 API（审计 / 用量）。
 * <p>所属模块：gateway-api。仅返回当前鉴权租户数据，禁止通过参数指定其他 tenantId。
 */
@RestController
@RequestMapping("/v1/observe")
public class ObserveController {

    private final AuditService auditService;
    private final UsageService usageService;

    /**
     * @param auditService 审计服务
     * @param usageService 用量服务
     */
    public ObserveController(AuditService auditService, UsageService usageService) {
        this.auditService = auditService;
        this.usageService = usageService;
    }

    /**
     * 查询本租户最近审计事件。
     *
     * @param limit 条数上限，默认 50
     * @return 事件列表
     */
    @GetMapping("/audit")
    public List<Map<String, Object>> listAudit(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        TenantContext tenant = TenantContextHolder.require();
        // 租户只来自鉴权上下文，忽略任何客户端传的 tenantId
        return auditService.listRecent(tenant.tenantId(), limit);
    }

    /**
     * 汇总本租户各指标用量。
     *
     * @return metric → total 列表
     */
    @GetMapping("/usage")
    public List<Map<String, Object>> usageSummary() {
        TenantContext tenant = TenantContextHolder.require();
        return usageService.summarizeForTenant(tenant.tenantId());
    }
}
