package com.agent.gateway.tools;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * tools 模块 Spring 配置。
 * <p>所属模块：tools。启用 {@link GatewaySecretProperties} 配置绑定。
 */
@Configuration
@EnableConfigurationProperties(GatewaySecretProperties.class)
public class ToolsConfiguration {
}
