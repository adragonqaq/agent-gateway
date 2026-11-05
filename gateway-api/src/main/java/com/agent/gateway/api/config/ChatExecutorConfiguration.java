package com.agent.gateway.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 聊天 / 确认续跑专用线程池。
 * <p>所属模块：gateway-api。把阻塞的 Agent 执行移出 HTTP 线程，使 SseEmitter 能立刻返回客户端。
 */
@Configuration
public class ChatExecutorConfiguration {

    /**
     * @return 聊天流式执行线程池
     */
    @Bean(name = "chatStreamExecutor")
    public Executor chatStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("chat-stream-");
        executor.initialize();
        return executor;
    }
}
