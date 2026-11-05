package com.agent.gateway.runtime;

import com.agent.gateway.observe.AuditService;
import com.agent.gateway.runtime.config.GatewayConfirmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 确认单过期扫描任务。
 * <p>所属模块：runtime。将超时 PENDING 落成 EXPIRED 并写审计，避免仅依赖惰性读取。
 * 可通过 {@code gateway.confirm.sweep-enabled=false} 关闭。
 */
@Component
@ConditionalOnProperty(name = "gateway.confirm.sweep-enabled", havingValue = "true", matchIfMissing = true)
public class ConfirmExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ConfirmExpiryScheduler.class);

    private final ConfirmGate confirmGate;
    private final AuditService auditService;
    private final GatewayConfirmProperties properties;

    /**
     * @param confirmGate 确认闸门
     * @param auditService 审计服务
     * @param properties 确认配置
     */
    public ConfirmExpiryScheduler(
            ConfirmGate confirmGate,
            AuditService auditService,
            GatewayConfirmProperties properties) {
        this.confirmGate = confirmGate;
        this.auditService = auditService;
        this.properties = properties;
    }

    /**
     * 周期扫描并过期到期确认单。
     */
    @Scheduled(fixedDelayString = "${gateway.confirm.sweep-interval-ms:30000}")
    public void sweepExpired() {
        List<PendingConfirmation> expired = confirmGate.expireDue(properties.getSweepBatchSize());
        for (PendingConfirmation item : expired) {
            auditService.record(
                    item.tenantId(),
                    "CONFIRM_EXPIRED",
                    item.id(),
                    "{\"taskId\":\"" + item.taskId() + "\",\"tool\":\"" + item.toolName() + "\"}");
        }
        if (!expired.isEmpty()) {
            log.info("确认单过期扫描完成，本批 {} 条", expired.size());
        }
    }
}
