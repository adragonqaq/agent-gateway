package com.agent.gateway.api.auth;

import com.agent.gateway.tenant.TenantContext;
import com.agent.gateway.tenant.TenantResolver;
import com.agent.gateway.tenant.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API Key 鉴权过滤器。
 * 解析 Bearer / X-Api-Key，将租户上下文绑定到当前请求线程，供后续 Controller 使用。
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final TenantResolver tenantResolver;

    /**
     * @param tenantResolver 根据原始 Key 解析租户与角色
     */
    public ApiKeyAuthFilter(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    /**
     * 健康检查与 actuator 路径跳过鉴权，避免探针被 401。
     *
     * @param request 当前 HTTP 请求
     * @return 为 true 时不进入本过滤器逻辑
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/health");
    }

    /**
     * 解析 Key、绑定租户上下文，并在请求结束时清理 ThreadLocal，防止线程复用串租户。
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String raw = extractKey(request);
            TenantContext context = tenantResolver.resolve(raw);
            // TenantContextHolder：把解析出的租户/角色绑定到请求线程，供下游 require/业务使用
            TenantContextHolder.set(context);
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + ex.getMessage() + "\"}");
        } finally {
            // TenantContextHolder：请求结束必须清理，避免线程池复用导致跨租户泄漏
            TenantContextHolder.clear();
        }
    }

    /**
     * 从 Authorization Bearer 或 X-Api-Key 提取原始 API Key。
     *
     * @param request 当前请求
     * @return 原始 Key；缺失时返回 null
     */
    private static String extractKey(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        String apiKey = request.getHeader("X-Api-Key");
        return apiKey == null ? null : apiKey.trim();
    }
}
