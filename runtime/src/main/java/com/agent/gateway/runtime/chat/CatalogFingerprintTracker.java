package com.agent.gateway.runtime.chat;

import com.agent.gateway.tools.ResolvedCatalog;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工具目录指纹跟踪器。
 * <p>所属模块：runtime。供 echo 模式与单测感知目录是否变化，不持有 Agent 实例。
 */
@Component
public class CatalogFingerprintTracker {

    private final ConcurrentHashMap<String, String> fingerprints = new ConcurrentHashMap<>();
    private final AtomicInteger rebuilds = new AtomicInteger();

    /**
     * 记录租户当前目录指纹；与上次不同则重建计数加一。
     *
     * @param tenantId 租户 ID
     * @param catalog 已解析目录
     * @return 当前快照（含全局重建代数）
     */
    public synchronized Snapshot getOrRebuild(String tenantId, ResolvedCatalog catalog) {
        String fp = catalog.fingerprint();
        String prev = fingerprints.put(tenantId, fp);
        if (prev == null || !prev.equals(fp)) {
            // 首次出现或工具集变化才计数，相同指纹视为可复用
            rebuilds.incrementAndGet();
        }
        return new Snapshot(tenantId, fp, rebuilds.get());
    }

    /**
     * @return 进程内累计的目录重建次数
     */
    public int rebuildCount() {
        return rebuilds.get();
    }

    /**
     * 某次指纹比对的结果快照。
     * <p>所属模块：runtime。
     *
     * @param tenantId 租户 ID
     * @param fingerprint 当前目录指纹
     * @param rebuildGeneration 全局重建代数
     */
    public record Snapshot(String tenantId, String fingerprint, int rebuildGeneration) {
    }
}
