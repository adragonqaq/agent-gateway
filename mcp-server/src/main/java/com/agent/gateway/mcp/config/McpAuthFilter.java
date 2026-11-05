package com.agent.gateway.mcp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * MCP 传输入口的 Bearer 校验过滤器。
 * <p>仅拦截 {@code /mcp}；未启用鉴权时直接放行。失败返回 JSON-RPC 风格 401，避免泄露内部细节。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class McpAuthFilter extends OncePerRequestFilter {

    private final McpAuthProperties properties;

    /**
     * @param properties 鉴权配置
     */
    public McpAuthFilter(McpAuthProperties properties) {
        this.properties = properties;
    }

    /**
     * 非 /mcp 路径跳过，避免误伤健康检查等未来端点。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.endsWith("/mcp");
    }

    /**
     * 校验 Authorization Bearer；通过则继续 FilterChain。
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        // 未配置 token 时保持开放，方便本地无鉴权冒烟
        if (!properties.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        String expected = "Bearer " + properties.getToken().trim();
        String actual = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (expected.equals(actual)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 统一 401，不区分缺头/错 token，降低探测面
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32001,\"message\":\"unauthorized\"},\"id\":null}");
    }
}
