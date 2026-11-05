package com.agent.gateway.tenant;

/**
 * 鉴权失败异常（无效/缺失 API Key 或租户不可用）。
 * 属于 tenant 模块；对外统一文案，避免通过错误细节枚举有效租户或 Key。
 */
public class UnauthorizedException extends RuntimeException {

    /**
     * @param message 对调用方可见的鉴权失败说明
     */
    public UnauthorizedException(String message) {
        super(message);
    }
}
