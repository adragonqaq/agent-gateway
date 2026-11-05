package com.agent.gateway.tenant.config;

import org.springframework.context.annotation.Configuration;

/**
 * tenant 模块 Spring 装配入口。
 * <p>组件已由 {@code GatewayApplication} 的 {@code scanBasePackages=com.agent.gateway} 扫描；
 * 本类仅作模块边界标记，避免在配置类上叠加 {@code @ComponentScan}+{@code @ConditionalOnBean}
 * （Boot 3.4 禁止该组合）。
 */
@Configuration
public class TenantConfiguration {
}
