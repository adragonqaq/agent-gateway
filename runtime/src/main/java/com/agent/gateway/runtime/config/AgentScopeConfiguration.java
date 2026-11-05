package com.agent.gateway.runtime.config;

import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * AgentScope 运行时的 Spring 配置。
 * <p>所属模块：runtime。仅在 agentscope 模式下提供聊天模型与内存状态存储。
 */
@Configuration
@EnableConfigurationProperties(GatewayAgentProperties.class)
public class AgentScopeConfiguration {

    /**
     * 按配置构建 OpenAI 兼容聊天模型。未配置 API Key 时拒绝启动。
     *
     * @param properties 网关 Agent 配置
     * @return 流式聊天模型
     * @throws IllegalStateException mode=agentscope 但未配置 api-key
     */
    @Bean
    @ConditionalOnProperty(name = "gateway.agent.mode", havingValue = "agentscope")
    public OpenAIChatModel openAIChatModel(GatewayAgentProperties properties) {
        GatewayAgentProperties.Chat chat = properties.getChat();
        if (!StringUtils.hasText(chat.getApiKey())) {
            // 没有密钥无法建连，尽早失败而不是在首个请求里才报错
            throw new IllegalStateException("gateway.agent.chat.api-key is required when mode=agentscope");
        }
        return OpenAIChatModel.builder()
                .apiKey(chat.getApiKey())
                .baseUrl(chat.getBaseUrl())
                .endpointPath(chat.getEndpointPath())
                .modelName(chat.getModel())
                .stream(chat.isStream())
                // 工具调用与原生结构化输出同时开启容易冲突，这里关闭后者
                .nativeStructuredOutputWithTools(false)
                .build();
    }

    /**
     * 进程内 Agent 状态存储，供确认中断后恢复工具调用。
     *
     * @return 内存状态存储
     */
    @Bean
    @ConditionalOnProperty(name = "gateway.agent.mode", havingValue = "agentscope")
    public InMemoryAgentStateStore agentStateStore() {
        return new InMemoryAgentStateStore();
    }
}
