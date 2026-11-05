package com.agent.gateway.runtime;

/**
 * 确认单已过期异常。
 * <p>所属模块：runtime。超时未确认时抛出，映射为 HTTP 410。
 */
public class ExpiredConfirmationException extends RuntimeException {

    /**
     * @param message 错误说明
     */
    public ExpiredConfirmationException(String message) {
        super(message);
    }
}
