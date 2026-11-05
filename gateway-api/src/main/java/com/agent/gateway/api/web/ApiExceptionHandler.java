package com.agent.gateway.api.web;

import com.agent.gateway.runtime.NotFoundException;
import com.agent.gateway.tenant.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局 API 异常映射。
 * 将领域异常转为稳定的 HTTP 状态与 JSON 错误体，避免堆栈泄漏给客户端。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 鉴权失败 → 401。
     *
     * @param ex 未授权异常
     * @return 错误响应
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> unauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
    }

    /**
     * 资源不存在 → 404。
     *
     * @param ex 未找到异常
     * @return 错误响应
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    /**
     * 非法状态（如并发超限）→ 429。
     *
     * @param ex 非法状态异常
     * @return 错误响应
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", ex.getMessage()));
    }
}
