package com.agent.gateway.api.auth;

import com.agent.gateway.tenant.AuthService;
import com.agent.gateway.tenant.JwtService;
import com.agent.gateway.tenant.UnauthorizedException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * 用户注册 / 登录 / 刷新 / 注销 / me。
 */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    public static final String ACCESS_COOKIE = "gw_access";
    public static final String REFRESH_COOKIE = "gw_refresh";

    private final AuthService authService;
    private final JwtService jwtService;
    private final boolean cookieSecure;
    private final int accessTtlSeconds;
    private final int refreshTtlSeconds;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            @Value("${gateway.auth.cookie-secure:false}") boolean cookieSecure,
            @Value("${gateway.auth.access-ttl-seconds:3600}") int accessTtlSeconds,
            @Value("${gateway.auth.refresh-ttl-seconds:1209600}") int refreshTtlSeconds) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.cookieSecure = cookieSecure;
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@RequestBody Map<String, String> body, HttpServletResponse response) {
        AuthService.AuthTokens tokens = authService.register(body.get("email"), body.get("password"));
        writeCookies(response, tokens);
        return authService.me(jwtService.requireUserId(tokens.accessToken(), "access"));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpServletResponse response) {
        AuthService.AuthTokens tokens = authService.login(body.get("email"), body.get("password"));
        writeCookies(response, tokens);
        return authService.me(jwtService.requireUserId(tokens.accessToken(), "access"));
    }

    @PostMapping("/refresh")
    public Map<String, String> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refresh = readCookie(request, REFRESH_COOKIE);
        if (refresh == null || refresh.isBlank()) {
            throw new UnauthorizedException("Refresh token required");
        }
        AuthService.AuthTokens tokens = authService.refresh(refresh);
        writeCookies(response, tokens);
        return Map.of("ok", "true");
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletResponse response) {
        clearCookie(response, ACCESS_COOKIE);
        clearCookie(response, REFRESH_COOKIE);
        return Map.of("ok", "true");
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        String access = readCookie(request, ACCESS_COOKIE);
        if (access == null || access.isBlank()) {
            throw new UnauthorizedException("Not logged in");
        }
        String userId = jwtService.requireUserId(access, "access");
        return authService.me(userId);
    }

    private void writeCookies(HttpServletResponse response, AuthService.AuthTokens tokens) {
        writeCookie(response, ACCESS_COOKIE, tokens.accessToken(), accessTtlSeconds);
        writeCookie(response, REFRESH_COOKIE, tokens.refreshToken(), refreshTtlSeconds);
    }

    private void writeCookie(HttpServletResponse response, String name, String value, int maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(Duration.ofSeconds(Math.max(0, maxAge)))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name) {
        writeCookie(response, name, "", 0);
    }

    static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
