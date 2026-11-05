package com.agent.gateway.runtime;

/**
 * 资源未找到异常。
 * <p>所属模块：runtime。用于确认单、任务等按租户查找失败时抛出。
 */
public class NotFoundException extends RuntimeException {

    /**
     * @param message 错误说明
     */
    public NotFoundException(String message) {
        super(message);
    }
}
