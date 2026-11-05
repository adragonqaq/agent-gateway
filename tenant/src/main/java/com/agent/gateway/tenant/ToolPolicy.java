package com.agent.gateway.tenant;

/**
 * 单租户下某工具的策略项（允许/拒绝及是否需确认）。
 * 属于 tenant 模块；按 tenant_id + tool_name 隔离，防止跨租户策略串用。
 * <ul>
 *   <li>{@code toolName} — 工具名</li>
 *   <li>{@code effect} — 如 ALLOW / DENY</li>
 *   <li>{@code requireConfirm} — 调用前是否需要人工确认门</li>
 * </ul>
 */
public record ToolPolicy(String toolName, String effect, boolean requireConfirm) {

    /**
     * 是否显式拒绝该工具。
     *
     * @return effect 为 DENY（忽略大小写）时为 true
     */
    public boolean denied() {
        return "DENY".equalsIgnoreCase(effect);
    }
}
