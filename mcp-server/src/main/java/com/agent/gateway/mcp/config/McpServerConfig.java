package com.agent.gateway.mcp.config;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 挂载 streamable HTTP 传输到 {@code /mcp}，并注册全部 SyncToolSpecification。
 */
@Configuration
public class McpServerConfig {

    @Bean
    public HttpServletStreamableServerTransportProvider transportProvider() {
        return HttpServletStreamableServerTransportProvider.builder().build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transportProvider) {
        return new ServletRegistrationBean<>(transportProvider, "/mcp");
    }

    @Bean
    public McpSyncServer mcpServer(
            HttpServletStreamableServerTransportProvider transportProvider,
            List<McpServerFeatures.SyncToolSpecification> toolSpecs) {
        requireReadOnlyHint(toolSpecs);
        return McpServer.sync(transportProvider)
                .serverInfo("agent-gateway-demo-mcp", "0.1.0")
                .tools(toolSpecs)
                .build();
    }

    static void requireReadOnlyHint(List<McpServerFeatures.SyncToolSpecification> toolSpecs) {
        List<String> undeclared = toolSpecs.stream()
                .filter(spec -> spec.tool().annotations() == null
                        || spec.tool().annotations().readOnlyHint() == null)
                .map(spec -> spec.tool().name())
                .toList();
        if (!undeclared.isEmpty()) {
            throw new IllegalStateException(
                    "MCP tools missing readOnlyHint: " + undeclared);
        }
    }
}
