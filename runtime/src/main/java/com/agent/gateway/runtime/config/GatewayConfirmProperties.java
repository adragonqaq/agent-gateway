package com.agent.gateway.runtime.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 人工确认闸门配置。
 * <p>所属模块：runtime。绑定 {@code gateway.confirm}。
 */
@ConfigurationProperties(prefix = "gateway.confirm")
public class GatewayConfirmProperties {

    /** 确认单超时秒数；超时后标记 EXPIRED。 */
    private int timeoutSeconds = 300;
    /** 是否启用定时过期扫描（惰性过期始终生效）。 */
    private boolean sweepEnabled = true;
    /** 扫描过期确认单的间隔毫秒。 */
    private long sweepIntervalMs = 30_000L;
    /** 每轮扫描最多处理条数。 */
    private int sweepBatchSize = 100;

    /**
     * @return 超时秒数
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * @param timeoutSeconds 超时秒数
     */
    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * @return 是否启用定时扫描
     */
    public boolean isSweepEnabled() {
        return sweepEnabled;
    }

    /**
     * @param sweepEnabled 是否启用定时扫描
     */
    public void setSweepEnabled(boolean sweepEnabled) {
        this.sweepEnabled = sweepEnabled;
    }

    /**
     * @return 扫描间隔毫秒
     */
    public long getSweepIntervalMs() {
        return sweepIntervalMs;
    }

    /**
     * @param sweepIntervalMs 扫描间隔毫秒
     */
    public void setSweepIntervalMs(long sweepIntervalMs) {
        this.sweepIntervalMs = sweepIntervalMs;
    }

    /**
     * @return 每轮最大处理条数
     */
    public int getSweepBatchSize() {
        return sweepBatchSize;
    }

    /**
     * @param sweepBatchSize 每轮最大处理条数
     */
    public void setSweepBatchSize(int sweepBatchSize) {
        this.sweepBatchSize = sweepBatchSize;
    }
}
