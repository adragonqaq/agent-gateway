package com.agent.gateway.observe;

import com.agent.gateway.tenant.TenantIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户 token 配额：读 {@code gw_tenant.token_quota}，对照用量强制限额。
 * <p>所属模块：observe。{@code token_quota} 为 NULL 表示不限额。
 */
@Service
public class QuotaService {

    private final JdbcTemplate jdbc;
    private final UsageService usageService;

    /**
     * @param jdbc JDBC
     * @param usageService 用量汇总
     */
    public QuotaService(JdbcTemplate jdbc, UsageService usageService) {
        this.jdbc = jdbc;
        this.usageService = usageService;
    }

    /**
     * @param tenantId 租户
     * @return 配额；null 表示不限额
     */
    public Long tokenQuota(String tenantId) {
        String tid = TenantIds.require(tenantId);
        List<Long> rows = jdbc.query(
                "SELECT token_quota FROM gw_tenant WHERE id = ?",
                (rs, i) -> {
                    long v = rs.getLong("token_quota");
                    return rs.wasNull() ? null : v;
                },
                tid);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 写入/清空配额。
     *
     * @param tenantId 租户
     * @param quota 上限；null 清空为不限额
     */
    public void setTokenQuota(String tenantId, Long quota) {
        String tid = TenantIds.require(tenantId);
        jdbc.update("UPDATE gw_tenant SET token_quota = ? WHERE id = ?", quota, tid);
    }

    /**
     * 用量快照：quota / used / remaining。
     *
     * @param tenantId 租户
     * @return 摘要 map
     */
    public Map<String, Object> snapshot(String tenantId) {
        String tid = TenantIds.require(tenantId);
        Long quota = tokenQuota(tid);
        long used = usageService.sumForTenant(tid, "total_tokens");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tokenQuota", quota);
        out.put("tokenUsed", used);
        out.put("tokenRemaining", quota == null ? null : Math.max(0L, quota - used));
        out.put("unlimited", quota == null);
        return out;
    }

    /**
     * 开跑前校验；超额抛 {@link QuotaExceededException}。
     *
     * @param tenantId 租户
     */
    public void requireTokenQuotaAvailable(String tenantId) {
        String tid = TenantIds.require(tenantId);
        Long quota = tokenQuota(tid);
        if (quota == null) {
            return;
        }
        long used = usageService.sumForTenant(tid, "total_tokens");
        if (used >= quota) {
            throw new QuotaExceededException(
                    "token quota exceeded: used=" + used + ", quota=" + quota,
                    used,
                    quota);
        }
    }
}
