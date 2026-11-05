package com.agent.gateway.api.admin;

/**
 * 创建租户的请求体。
 *
 * @param name 租户显示名称
 * @param modelTier 模型档位（决定可用模型与配额策略）
 * @param credentialBinding 上游模型凭证绑定标识
 * @param maxConcurrent 最大并发运行数；为空时由服务端给默认值
 */
public record CreateTenantRequest(
        String name,
        String modelTier,
        String credentialBinding,
        Integer maxConcurrent
) {
}
