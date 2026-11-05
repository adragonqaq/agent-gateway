package com.agent.gateway.runtime.config;

import com.agent.gateway.runtime.state.JdbcAgentStateStore;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

/**
 * AgentScope 运行时的 Spring 配置。
 * <p>所属模块：runtime。仅在 agentscope 模式下提供聊天模型与 Agent 状态存储。
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
            throw new IllegalStateException("gateway.agent.chat.api-key is required when mode=agentscope");
        }
        return OpenAIChatModel.builder()
                .apiKey(chat.getApiKey())
                .baseUrl(chat.getBaseUrl())
                .endpointPath(chat.getEndpointPath())
                .modelName(chat.getModel())
                .stream(chat.isStream())
                .nativeStructuredOutputWithTools(false)
                .build();
    }

    /**
     * Agent 状态存储：默认 JDBC；{@code gateway.agent.state-store=memory} 时用内存。
     *
     * @param jdbc JDBC 模板
     * @param stateStoreType memory 或 jdbc
     * @return AgentStateStore
     */
    @Bean
    @ConditionalOnProperty(name = "gateway.agent.mode", havingValue = "agentscope")
    public AgentStateStore agentStateStore(
            JdbcTemplate jdbc,
            @Value("${gateway.agent.state-store:jdbc}") String stateStoreType) {
        if ("memory".equalsIgnoreCase(stateStoreType)) {
            return new InMemoryAgentStateStore();
        }
        return new JdbcAgentStateStore(jdbc);
    }
}
