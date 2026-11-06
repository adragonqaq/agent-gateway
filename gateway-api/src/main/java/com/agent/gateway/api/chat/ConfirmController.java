package com.agent.gateway.api.chat;

import com.agent.gateway.api.auth.TenantContextHolder;
import com.agent.gateway.api.sse.SseSupport;
import com.agent.gateway.observe.TraceSupport;
import com.agent.gateway.runtime.chat.AgentChatService;
import com.agent.gateway.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 人工确认门禁 API。
 * 批准或拒绝后异步续跑 Agent，并以 SSE 边跑边推后续事件。
 */
@RestController
@RequestMapping("/v1/confirms")
public class ConfirmController {

    private final AgentChatService agentChatService;
    private final Executor chatStreamExecutor;

    /**
     * @param agentChatService 聊天编排（含确认续跑）
     * @param chatStreamExecutor 流式执行线程池
     */
    public ConfirmController(
            AgentChatService agentChatService,
            @Qualifier("chatStreamExecutor") Executor chatStreamExecutor) {
        this.agentChatService = agentChatService;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    /**
     * 批准待确认工具并续跑。
     */
    @PostMapping(value = "/{confirmId}/approve", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter approve(@PathVariable String confirmId) {
        return resume(confirmId, true);
    }

    /**
     * 拒绝待确认工具并续跑。
     */
    @PostMapping(value = "/{confirmId}/deny", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter deny(@PathVariable String confirmId) {
        return resume(confirmId, false);
    }

    private SseEmitter resume(String confirmId, boolean approved) {
        TenantContext tenant = TenantContextHolder.require();
        SseEmitter emitter = new SseEmitter(300_000L);
        Object lock = new Object();
        chatStreamExecutor.execute(TraceSupport.wrap(() -> {
            try {
                agentChatService.resumeAfterConfirmStreaming(
                        tenant,
                        confirmId,
                        approved,
                        5,
                        ready -> {
                            Map<String, Object> meta = new LinkedHashMap<>();
                            meta.put("taskId", ready.taskId());
                            meta.put("confirmId", confirmId);
                            meta.put("approved", approved);
                            meta.put("traceId", ready.traceId());
                            SseSupport.send(emitter, lock, "meta", meta);
                        },
                        event -> SseSupport.send(emitter, lock, event.type(), event.data()));
                SseSupport.complete(emitter, lock);
            } catch (Exception ex) {
                SseSupport.send(emitter, lock, "error", ex.getMessage() == null ? "error" : ex.getMessage());
                SseSupport.completeWithError(emitter, lock, ex);
            }
        }));
        return emitter;
    }
}
