package com.agent.gateway.api.auth;

import com.agent.gateway.tenant.TenantContext;

public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> HOLDER = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(TenantContext context) {
        HOLDER.set(context);
    }

    public static TenantContext get() {
        return HOLDER.get();
    }

    public static TenantContext require() {
        TenantContext ctx = HOLDER.get();
        if (ctx == null) {
            throw new IllegalStateException("TenantContext is not bound");
        }
        return ctx;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
