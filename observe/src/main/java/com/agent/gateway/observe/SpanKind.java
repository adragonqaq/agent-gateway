package com.agent.gateway.observe;

/**
 * Trace Span 类型。
 * <p>所属模块：observe。
 */
public enum SpanKind {
    /** 一次 chat / confirm-resume 运行 */
    RUN,
    /** 工具调用 */
    TOOL,
    /** 人工确认挂起 */
    CONFIRM,
    /** 模型调用（轻量整轮） */
    LLM
}
