package com.agent.gateway.runtime;

import com.agent.gateway.tenant.TenantIds;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRunGate implements RunGate {

    private final Map<String, Set<String>> running = new ConcurrentHashMap<>();

    @Override
    public AcquireResult tryAcquire(String tenantId, String taskId, int maxConcurrent) {
        String tid = TenantIds.require(tenantId);
        Set<String> tasks = running.computeIfAbsent(tid, k -> ConcurrentHashMap.newKeySet());
        synchronized (tasks) {
            if (tasks.size() >= maxConcurrent) {
                return AcquireResult.rejected("tenant concurrency limit reached");
            }
            tasks.add(taskId);
            return AcquireResult.ok();
        }
    }

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
