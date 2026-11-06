package com.agent.gateway.observe;

import java.util.UUID;

/**
 * Trace / Span ID 生成工具。
 * <p>所属模块：observe。
 */
public final class TraceIds {

    private TraceIds() {
    }

    /**
     * @return 新的链路 ID，前缀 {@code trc_}
     */
    public static String newTraceId() {
        return "trc_" + shortUuid();
    }

    /**
     * @return 新的 Span ID，前缀 {@code spn_}
     */
    public static String newSpanId() {
        return "spn_" + shortUuid();
    }

    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
