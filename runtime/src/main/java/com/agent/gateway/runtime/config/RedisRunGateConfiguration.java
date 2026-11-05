package com.agent.gateway.runtime.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis RunGate 所需的 Redisson 客户端装配。
 * <p>所属模块：runtime。仅在 {@code gateway.run-gate.type=redis} 时启用，避免默认依赖本机 Redis。
 */
@Configuration
@ConditionalOnProperty(name = "gateway.run-gate.type", havingValue = "redis")
public class RedisRunGateConfiguration {

    /**
     * @param address Redis 地址，如 redis://127.0.0.1:6379
     * @return Redisson 客户端
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${gateway.run-gate.redis-address:redis://127.0.0.1:6379}") String address) {
        Config config = new Config();
        config.useSingleServer().setAddress(address);
        return Redisson.create(config);
    }
}
