package com.agent.gateway.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * 浏览器跨域配置，供独立前端（如 Vite :5173）直连 gateway。
 * <p>所属模块：gateway-api。允许的 Origin 通过 {@code gateway.cors.allowed-origins} 配置。
 */
@Configuration
public class CorsConfiguration {

    /**
     * 注册全局 CORS 过滤器。
     *
     * @param allowedOriginsCsv 逗号分隔的 Origin 白名单
     * @return CorsFilter Bean
     */
    @Bean
    public CorsFilter corsFilter(
            @Value("${gateway.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
            String allowedOriginsCsv) {
        // 解析白名单：本地 Vite 默认端口；生产应收紧
        List<String> origins = Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        // 预检缓存，减少 OPTIONS 次数
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 覆盖全部 API，含 /v1 与 /admin
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
