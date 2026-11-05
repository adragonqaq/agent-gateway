package com.agent.gateway.api.chat;

import com.agent.gateway.api.auth.TenantContextHolder;
import com.agent.gateway.runtime.chat.AgentChatService;
import com.agent.gateway.runtime.chat.AgentEvent;
import com.agent.gateway.tenant.TenantContext;
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

/**
 * 对话运行面 API。
 * 以 SSE 推送 Agent 事件，并支持按 taskId 取消运行。
 */
@RestController
@RequestMapping("/v1")
public class ChatController {

    private final AgentChatService agentChatService;

    /**
     * @param agentChatService Agent 对话编排服务
     */
    public ChatController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    /**
     * 发起一轮对话并以 SSE 流式返回事件。
     *
     * @param request 对话请求（会话 ID 与用户消息）
     * @return SSE 发射器
     * @throws IOException 发送 SSE 事件失败时抛出
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) throws IOException {
        // TenantContextHolder：对话必须绑定调用方租户，隔离配额、工具策略与凭证
        TenantContext tenant = TenantContextHolder.require();
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? "conv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : request.conversationId();
        AgentChatService.ChatRun run = agentChatService.start(
                tenant, conversationId, request.message(), 5);
        SseEmitter emitter = new SseEmitter(60_000L);
        // SSE emit：先推 meta（taskId/conversationId），客户端据此关联后续事件与取消
        emitter.send(SseEmitter.event().name("meta").data(Map.of(
                "taskId", run.taskId(),
                "conversationId", conversationId)));
        for (AgentEvent event : run.events()) {
            // SSE emit：按事件类型原样推送，保持与运行时 AgentEvent 契约一致
            emitter.send(SseEmitter.event().name(event.type()).data(event.data()));
        }
        emitter.complete();
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
        // TenantContextHolder：取消必须带租户，防止跨租户打断他人任务
        TenantContext tenant = TenantContextHolder.require();
        // cancel：通知运行时停止该 task，释放并发名额并中断进行中的工具调用
        agentChatService.cancel(tenant.tenantId(), taskId);
        return Map.of("status", "cancelled");
    }
}
