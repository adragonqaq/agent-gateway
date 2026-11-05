package com.agent.gateway.runtime;

/**
 * 工具调用人工确认决策状态。
 * <p>所属模块：runtime。由 {@link ConfirmGate} 持久化并驱动确认流程。
 */
public enum ConfirmDecision {
    /** 等待用户确认。 */
    PENDING,
    /** 用户已确认，允许继续执行。 */
    CONFIRMED,
    /** 用户已拒绝。 */
    DENIED,
    /** 超过确认超时，自动过期。 */
    EXPIRED
}
