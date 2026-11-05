package com.agent.gateway.memory;

import java.util.Optional;

/**
 * 会话 Agent 状态存储端口（按租户 + conversation 隔离）。
 * 属于 memory 模块；实现方必须强制 tenant_id 条件，防止跨租户读写会话状态。
 */
public interface SessionStateStore {

    /**
     * 保存（upsert）会话状态载荷。
     *
     * @param tenantId 租户 ID
     * @param conversationId 会话/对话 ID
     * @param payload 序列化后的状态正文
     */
    void save(String tenantId, String conversationId, String payload);

    /**
     * 加载会话状态载荷。
     *
     * @param tenantId 租户 ID
     * @param conversationId 会话/对话 ID
     * @return 存在则返回 payload
     */
    Optional<String> load(String tenantId, String conversationId);
}
