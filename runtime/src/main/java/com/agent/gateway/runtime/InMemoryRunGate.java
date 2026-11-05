package com.agent.gateway.runtime;

import com.agent.gateway.tenant.TenantIds;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内内存实现的运行闸门。
 * <p>所属模块：runtime。按租户维护正在运行的 taskId 集合，适合单实例部署。
 */
@Component
public class InMemoryRunGate implements RunGate {

    private final Map<String, Set<String>> running = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public AcquireResult tryAcquire(String tenantId, String taskId, int maxConcurrent) {
        String tid = TenantIds.require(tenantId);
        Set<String> tasks = running.computeIfAbsent(tid, k -> ConcurrentHashMap.newKeySet());
        synchronized (tasks) {
            // 检查上限与占槽必须原子，否则并发请求会突破租户限额
            if (tasks.size() >= maxConcurrent) {
                return AcquireResult.rejected("tenant concurrency limit reached");
            }
            tasks.add(taskId);
            return AcquireResult.ok();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void release(String tenantId, String taskId) {
        String tid = TenantIds.require(tenantId);
        Set<String> tasks = running.get(tid);
        if (tasks != null) {
            synchronized (tasks) {
                tasks.remove(taskId);
            }
        }
    }
}
