package com.agent.gateway.api.auth;

import com.agent.gateway.tenant.AuthService;
import com.agent.gateway.tenant.JwtService;
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
 * 双通道鉴权：Bearer/X-Api-Key 或 HttpOnly JWT Cookie + X-Project-Id。
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String PROJECT_HEADER = "X-Project-Id";

    private final TenantResolver tenantResolver;
    private final JwtService jwtService;
    private final AuthService authService;

    public ApiKeyAuthFilter(TenantResolver tenantResolver, JwtService jwtService, AuthService authService) {
        this.tenantResolver = tenantResolver;
        this.jwtService = jwtService;
        this.authService = authService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.equals("/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            TenantContext context = resolve(request);
            TenantContextHolder.set(context);
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + ex.getMessage().replace("\"", "'") + "\"}");
        } finally {
            TenantContextHolder.clear();
        }
    }

    private TenantContext resolve(HttpServletRequest request) {
        String rawKey = extractKey(request);
        if (rawKey != null && !rawKey.isBlank()) {
            return tenantResolver.resolve(rawKey);
        }
        String access = AuthController.readCookie(request, AuthController.ACCESS_COOKIE);
        if (access == null || access.isBlank()) {
            throw new UnauthorizedException("API key or login required");
        }
        String userId = jwtService.requireUserId(access, "access");
        String projectId = request.getHeader(PROJECT_HEADER);
        if (projectId == null || projectId.isBlank()) {
            throw new UnauthorizedException("X-Project-Id header required");
        }
        return authService.resolveJwtContext(userId, projectId.trim());
    }

    private static String extractKey(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            // JWT 也可能被误塞进 Authorization；仅 sk_ 前缀当 API Key
            if (token.startsWith("sk_")) {
                return token;
            }
            return null;
        }
        String apiKey = request.getHeader("X-Api-Key");
        return apiKey == null ? null : apiKey.trim();
    }
}
