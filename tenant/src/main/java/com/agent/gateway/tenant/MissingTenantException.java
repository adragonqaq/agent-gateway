package com.agent.gateway.tenant;

/**
 * 缺少或非法租户 ID 时抛出。
 * 属于 tenant 模块；用于在写库/读库前强制租户隔离边界，避免空 tenant_id 串租。
 */
public class MissingTenantException extends RuntimeException {

    /**
     * @param message 说明为何缺少租户（如 blank）
     */
    public MissingTenantException(String message) {
        super(message);
    }
}
