package com.agent.gateway.api.chat;

import com.agent.gateway.api.auth.ApiKeyAuthFilter;
import com.agent.gateway.api.web.ApiExceptionHandler;
import com.agent.gateway.runtime.NotFoundException;
import com.agent.gateway.runtime.chat.AgentChatService;
import com.agent.gateway.tenant.AuthService;
import com.agent.gateway.tenant.JwtService;
import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tenant.TenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 对话 API 的 MockMvc 冒烟：鉴权拒绝与跨租户 cancel 404。
 */
class ChatApiMvcTest {

    private MockMvc mockMvc;
    private TenantResolver tenantResolver;
    private AgentChatService agentChatService;

    @BeforeEach
    void setUp() {
        tenantResolver = mock(TenantResolver.class);
        agentChatService = mock(AgentChatService.class);
        Executor sync = Runnable::run;
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(agentChatService, sync))
                .addFilters(new ApiKeyAuthFilter(
                        tenantResolver, mock(JwtService.class), mock(AuthService.class)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void chatRequiresApiKey() throws Exception {
        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("API key or login required"));
    }

    @Test
    void cancelForeignTenantTaskReturns404() throws Exception {
        TenantContext tenant = new TenantContext("tA", "keyA", "RUNTIME", "standard", "platform");
        when(tenantResolver.resolve("sk_a")).thenReturn(tenant);
        doThrow(new NotFoundException("task not found"))
                .when(agentChatService).cancel(eq(tenant), eq("task_foreign"));

        mockMvc.perform(post("/v1/runs/task_foreign/cancel")
                        .header("Authorization", "Bearer sk_a"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("task not found"));

        verify(agentChatService).cancel(tenant, "task_foreign");
    }

    @Test
    void cancelWithValidKeyReturnsCancelled() throws Exception {
        TenantContext tenant = new TenantContext("tA", "keyA", "RUNTIME", "standard", "platform");
        when(tenantResolver.resolve("sk_a")).thenReturn(tenant);

        mockMvc.perform(post("/v1/runs/task_1/cancel")
                        .header("X-Api-Key", "sk_a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));

        verify(agentChatService).cancel(tenant, "task_1");
    }
}
