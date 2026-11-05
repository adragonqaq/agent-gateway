package com.agent.gateway.api.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 线程安全的 SSE 发送辅助。
 * <p>所属模块：gateway-api。后台执行线程与容器写回可能并发，发送需加锁。
 */
public final class SseSupport {

    private SseSupport() {
    }

    /**
     * 向发射器写入命名事件；发送失败时完成并带上错误。
     *
     * @param emitter SSE 发射器
     * @param lock 与该 emitter 绑定的互斥锁
     * @param name 事件名
     * @param data 事件数据
     */
    public static void send(SseEmitter emitter, Object lock, String name, Object data) {
        synchronized (lock) {
            try {
                emitter.send(SseEmitter.event().name(name).data(data));
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    /**
     * 正常结束 SSE。
     *
     * @param emitter SSE 发射器
     * @param lock 互斥锁
     */
    public static void complete(SseEmitter emitter, Object lock) {
        synchronized (lock) {
            emitter.complete();
        }
    }

    /**
     * 异常结束 SSE。
     *
     * @param emitter SSE 发射器
     * @param lock 互斥锁
     * @param error 错误
     */
    public static void completeWithError(SseEmitter emitter, Object lock, Throwable error) {
        synchronized (lock) {
            emitter.completeWithError(error);
        }
    }
}
