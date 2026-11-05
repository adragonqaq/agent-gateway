package com.agent.gateway.runtime.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 确认闸门相关 Spring 配置。
 * <p>所属模块：runtime。启用 {@link GatewayConfirmProperties}。
 */
@Configuration
@EnableConfigurationProperties(GatewayConfirmProperties.class)
public class ConfirmConfiguration {
}
