package com.agent.gateway.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置。
 * <p>所属模块：gateway-api。声明全局 API Key 鉴权方案，便于前端与联调对照。
 */
@Configuration
public class OpenApiConfiguration {

    /**
     * @return OpenAPI 根文档（标题、鉴权方案）
     */
    @Bean
    public OpenAPI agentGatewayOpenApi() {
        final String scheme = "ApiKeyAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Agent Gateway API")
                        .description("多租户 ReAct Agent 网关：管理面 + 数据面（SSE 对话 / 确认续跑）")
                        .version("0.1.0"))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("API Key")
                                .description("Bearer sk_... 或请求头 X-Api-Key")))
                .addSecurityItem(new SecurityRequirement().addList(scheme));
    }
}
