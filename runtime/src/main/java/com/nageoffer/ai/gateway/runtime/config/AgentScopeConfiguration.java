package com.nageoffer.ai.gateway.runtime.config;

import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(GatewayAgentProperties.class)
public class AgentScopeConfiguration {

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

    @Bean
    @ConditionalOnProperty(name = "gateway.agent.mode", havingValue = "agentscope")
    public InMemoryAgentStateStore agentStateStore() {
        return new InMemoryAgentStateStore();
    }
}
