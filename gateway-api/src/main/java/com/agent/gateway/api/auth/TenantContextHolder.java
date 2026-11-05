package com.agent.gateway.api.auth;

import com.agent.gateway.tenant.TenantContext;

/**
 * 请求线程级租户上下文持有者。
 * 由鉴权过滤器写入，Controller / 管理面通过 {@link #require()} 读取当前调用方身份。
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> HOLDER = new ThreadLocal<>();

    /**
     * 禁止实例化；仅提供静态访问。
     */
    private TenantContextHolder() {
    }

    /**
     * 绑定当前请求的租户上下文。
     *
     * @param context 已解析的租户与角色信息
     */
    public static void set(TenantContext context) {
        HOLDER.set(context);
    }

    /**
     * 获取当前线程绑定的租户上下文（可能为 null）。
     *
     * @return 租户上下文，未绑定时为 null
     */
    public static TenantContext get() {
        return HOLDER.get();
    }

    /**
     * 强制获取当前租户上下文；未绑定则视为编程错误。
     *
     * @return 非空租户上下文
     */
    public static TenantContext require() {
        TenantContext ctx = HOLDER.get();
        if (ctx == null) {
            throw new IllegalStateException("TenantContext is not bound");
        }
        return ctx;
    }

    /**
     * 清除当前线程绑定，防止线程复用串租户。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
