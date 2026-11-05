package com.agent.gateway.api.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 线程安全的 SSE 发送辅助。
 * <p>所属模块：gateway-api。后台执行线程与容器写回可能并发，发送需加锁。
 */
public final class SseSupport {

    private static final ObjectMapper JSON = new ObjectMapper();

    private SseSupport() {
    }

    /**
     * 向发射器写入命名事件；发送失败时完成并带上错误。
     * <p>字符串先 {@code ObjectMapper.writeValueAsString} 再写出（线上为
     * {@code data:" world"}），避免 SSE 剥掉 {@code data:} 后首个空格导致英文丢空格。
     * Spring 的 {@code MediaType.APPLICATION_JSON} 对纯 String 在部分版本仍会写成裸文本，不可靠。
     *
     * @param emitter SSE 发射器
     * @param lock 与该 emitter 绑定的互斥锁
     * @param name 事件名
     * @param data 事件数据
     */
    public static void send(SseEmitter emitter, Object lock, String name, Object data) {
        synchronized (lock) {
            try {
                Object payload = data;
                if (data instanceof String text) {
                    try {
                        payload = JSON.writeValueAsString(text);
                    } catch (JsonProcessingException ex) {
                        payload = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
                    }
                }
                emitter.send(SseEmitter.event().name(name).data(payload));
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
