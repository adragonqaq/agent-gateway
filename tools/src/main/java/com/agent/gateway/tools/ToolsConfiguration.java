package com.agent.gateway.tools;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewaySecretProperties.class)
public class ToolsConfiguration {
}
