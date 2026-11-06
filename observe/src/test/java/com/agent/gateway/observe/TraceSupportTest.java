package com.agent.gateway.observe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceSupportTest {

    @AfterEach
    void tearDown() {
        TraceContextHolder.clear();
        MDC.clear();
    }

    @Test
    void wrapPropagatesTraceContextAndMdcToWorkerThread() throws Exception {
        TraceContext ctx = new TraceContext("ten_1", "conv_1", "trc_1", "task_1", "spn_1");
        TraceContextHolder.set(ctx);

        AtomicReference<String> seenTrace = new AtomicReference<>();
        AtomicReference<String> seenMdc = new AtomicReference<>();
        Thread worker = new Thread(TraceSupport.wrap(() -> {
            seenTrace.set(TraceContextHolder.get() == null ? null : TraceContextHolder.get().traceId());
            seenMdc.set(MDC.get(TraceContextHolder.MDC_TRACE_ID));
        }));
        worker.start();
        worker.join();

        assertEquals("trc_1", seenTrace.get());
        assertEquals("trc_1", seenMdc.get());
        // 调用方线程上下文仍在
        assertEquals("trc_1", TraceContextHolder.get().traceId());
    }

    @Test
    void clearRemovesMdc() {
        TraceContextHolder.set(new TraceContext("t", "c", "trc", "task", "spn"));
        TraceContextHolder.clear();
        assertNull(TraceContextHolder.get());
        assertNull(MDC.get(TraceContextHolder.MDC_TRACE_ID));
    }
}
