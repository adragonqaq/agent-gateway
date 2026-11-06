package com.agent.gateway.observe;

/**
 * 租户用量超出限额。
 * <p>所属模块：observe。由网关映射为 HTTP 429。
 */
public class QuotaExceededException extends RuntimeException {

    private final long used;
    private final long quota;

    /**
     * @param message 说明
     * @param used 已用量
     * @param quota 上限
     */
    public QuotaExceededException(String message, long used, long quota) {
        super(message);
        this.used = used;
        this.quota = quota;
    }

    public long used() {
        return used;
    }

    public long quota() {
        return quota;
    }
}
