package com.agent.gateway.api.chat;

import com.agent.gateway.api.auth.TenantContextHolder;
import com.agent.gateway.runtime.chat.AgentChatService;
import com.agent.gateway.runtime.chat.AgentEvent;
import com.agent.gateway.tenant.TenantContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * 人工确认门禁 API。
 * 批准或拒绝后立即续跑 Agent，并以 SSE 推送后续事件。
 */
@RestController
@RequestMapping("/v1/confirms")
public class ConfirmController {

    private final AgentChatService agentChatService;

    /**
     * @param agentChatService 聊天编排（含确认续跑）
     */
    public ConfirmController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    /**
     * 批准待确认工具并续跑。
     *
     * @param confirmId 确认单 ID
     * @return SSE 事件流（含续跑产生的 tool/answer/done 等）
     */
    @PostMapping(value = "/{confirmId}/approve", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter approve(@PathVariable String confirmId) throws IOException {
        return resume(confirmId, true);
    }

    /**
     * 拒绝待确认工具并续跑（拒绝文案由执行器/框架产出）。
     *
     * @param confirmId 确认单 ID
     * @return SSE 事件流
     */
    @PostMapping(value = "/{confirmId}/deny", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter deny(@PathVariable String confirmId) throws IOException {
        return resume(confirmId, false);
    }

    /**
     * 统一续跑入口：鉴权 → 结算确认单 → 推送 SSE。
     */
    private SseEmitter resume(String confirmId, boolean approved) throws IOException {
        // 必须绑定当前租户，防止跨租户操作他人确认单
        TenantContext tenant = TenantContextHolder.require();
        AgentChatService.ChatRun run = agentChatService.resumeAfterConfirm(tenant, confirmId, approved, 5);
        SseEmitter emitter = new SseEmitter(60_000L);
        emitter.send(SseEmitter.event().name("meta").data(Map.of(
                "taskId", run.taskId(),
                "confirmId", confirmId,
                "approved", approved)));
        for (AgentEvent event : run.events()) {
            emitter.send(SseEmitter.event().name(event.type()).data(event.data()));
        }
        emitter.complete();
        return emitter;
    }
}
