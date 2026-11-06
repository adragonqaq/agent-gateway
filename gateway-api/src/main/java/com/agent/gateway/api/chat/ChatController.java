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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 对话运行面 API。
 * 以异步 SSE 边跑边推 Agent 事件，并支持按 taskId 取消运行。
 */
@RestController
@RequestMapping("/v1")
public class ChatController {

    private final AgentChatService agentChatService;
    private final Executor chatStreamExecutor;

    /**
     * @param agentChatService Agent 对话编排服务
     * @param chatStreamExecutor 流式执行线程池
     */
    public ChatController(
            AgentChatService agentChatService,
            @Qualifier("chatStreamExecutor") Executor chatStreamExecutor) {
        this.agentChatService = agentChatService;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    /**
     * 发起一轮对话并以 SSE 流式返回事件（边跑边推）。
     *
     * @param request 对话请求
     * @return 立刻返回的 SSE 发射器
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        TenantContext tenant = TenantContextHolder.require();
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? "conv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : request.conversationId();

        SseEmitter emitter = new SseEmitter(300_000L);
        Object lock = new Object();

        chatStreamExecutor.execute(TraceSupport.wrap(() -> {
            try {
                agentChatService.startStreaming(
                        tenant,
                        conversationId,
                        request.message(),
                        5,
                        ready -> {
                            Map<String, Object> meta = new LinkedHashMap<>();
                            meta.put("taskId", ready.taskId());
                            meta.put("conversationId", conversationId);
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

    /**
     * 取消指定任务的运行。
     *
     * @param taskId 运行任务 ID
     * @return 取消结果状态
     */
    @PostMapping("/runs/{taskId}/cancel")
    public Map<String, String> cancel(@PathVariable String taskId) {
        TenantContext tenant = TenantContextHolder.require();
        agentChatService.cancel(tenant, taskId);
        return Map.of("status", "cancelled");
    }
}
