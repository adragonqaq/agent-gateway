package com.nageoffer.ai.gateway.api.chat;

import com.nageoffer.ai.gateway.api.auth.TenantContextHolder;
import com.nageoffer.ai.gateway.runtime.chat.AgentChatService;
import com.nageoffer.ai.gateway.runtime.chat.AgentEvent;
import com.nageoffer.ai.gateway.tenant.TenantContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class ChatController {

    private final AgentChatService agentChatService;

    public ChatController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) throws IOException {
        TenantContext tenant = TenantContextHolder.require();
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? "conv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : request.conversationId();
        AgentChatService.ChatRun run = agentChatService.start(
                tenant, conversationId, request.message(), 5);
        SseEmitter emitter = new SseEmitter(60_000L);
        emitter.send(SseEmitter.event().name("meta").data(Map.of(
                "taskId", run.taskId(),
                "conversationId", conversationId)));
        for (AgentEvent event : run.events()) {
            emitter.send(SseEmitter.event().name(event.type()).data(event.data()));
        }
        emitter.complete();
        return emitter;
    }

    @PostMapping("/runs/{taskId}/cancel")
    public Map<String, String> cancel(@PathVariable String taskId) {
        TenantContext tenant = TenantContextHolder.require();
        agentChatService.cancel(tenant.tenantId(), taskId);
        return Map.of("status", "cancelled");
    }
}
