package com.agent.gateway.tools;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户已解析的工具目录快照。
 * <p>所属模块：tools。供 runtime 构建 Agent Toolkit，并通过指纹检测目录变更。
 *
 * @param tenantId 租户 ID
 * @param tools 可见工具列表
 */
public record ResolvedCatalog(String tenantId, List<CatalogTool> tools) {

    /**
     * 计算目录指纹（SHA-256），用于判断是否需要重建 Agent。
     *
     * @return 十六进制指纹字符串
     * @throws IllegalStateException 当摘要算法不可用时
     */
    public String fingerprint() {
        // 指纹只纳入名称、可用性与确认标记，说明文案变化不触发 Agent 重建
        String raw = tenantId + "|" + tools.stream()
                .map(t -> t.name() + ":" + t.available() + ":" + t.requireConfirm())
                .sorted()
                .collect(Collectors.joining(","));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
