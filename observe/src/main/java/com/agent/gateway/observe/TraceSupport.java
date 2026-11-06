package com.agent.gateway.observe;

import org.slf4j.MDC;

import java.util.Map;

/**
 * 将 TraceContext + MDC 传播到异步任务。
 * <p>所属模块：observe。chat SSE 跑在线程池时必须包装，否则日志丢失 traceId。
 */
public final class TraceSupport {

    private TraceSupport() {
    }

    /**
     * 包装 Runnable：在目标线程恢复调用方的 TraceContext 与 MDC，结束后清理。
     *
     * @param task 业务任务
     * @return 可提交给 Executor 的包装任务
     */
    public static Runnable wrap(Runnable task) {
        TraceContext captured = TraceContextHolder.get();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            TraceContext previousCtx = TraceContextHolder.get();
            try {
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                } else {
                    MDC.clear();
                }
                TraceContextHolder.set(captured);
                task.run();
            } finally {
                if (previousMdc != null) {
                    MDC.setContextMap(previousMdc);
                } else {
                    MDC.clear();
                }
                TraceContextHolder.set(previousCtx);
            }
        };
    }
}
