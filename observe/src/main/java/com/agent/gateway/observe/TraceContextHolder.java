package com.agent.gateway.observe;

import org.slf4j.MDC;

/**
 * TraceContext 的 ThreadLocal 持有者，并同步写入 SLF4J MDC。
 * <p>所属模块：observe。异步线程须用 {@link TraceSupport} 拷贝。
 */
public final class TraceContextHolder {

    private static final ThreadLocal<TraceContext> CTX = new ThreadLocal<>();

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_TASK_ID = "taskId";
    public static final String MDC_TENANT_ID = "tenantId";
    public static final String MDC_CONVERSATION_ID = "conversationId";

    private TraceContextHolder() {
    }

    /**
     * 绑定上下文并刷新 MDC。
     *
     * @param context 链路上下文；null 则清理
     */
    public static void set(TraceContext context) {
        if (context == null) {
            clear();
            return;
        }
        CTX.set(context);
        MDC.put(MDC_TRACE_ID, nullToEmpty(context.traceId()));
        MDC.put(MDC_TASK_ID, nullToEmpty(context.taskId()));
        MDC.put(MDC_TENANT_ID, nullToEmpty(context.tenantId()));
        MDC.put(MDC_CONVERSATION_ID, nullToEmpty(context.conversationId()));
    }

    /**
     * @return 当前线程上下文；未绑定则为 null
     */
    public static TraceContext get() {
        return CTX.get();
    }

    /**
     * 清理 ThreadLocal 与 MDC。
     */
    public static void clear() {
        CTX.remove();
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_TASK_ID);
        MDC.remove(MDC_TENANT_ID);
        MDC.remove(MDC_CONVERSATION_ID);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
