package com.agent.gateway.runtime;

import com.agent.gateway.tenant.TenantIds;
import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的运行闸门。
 * <p>所属模块：runtime。多实例共享租户并发计数；条目带 TTL，进程崩溃后可自动回收名额。
 */
@Component
@ConditionalOnProperty(name = "gateway.run-gate.type", havingValue = "redis")
public class RedisRunGate implements RunGate {

    private static final String KEY_PREFIX = "gw:run:";
    private static final String LOCK_PREFIX = "gw:run-lock:";
    private static final long ENTRY_TTL_MINUTES = 30;

    private final RedissonClient redisson;

    /**
     * @param redisson Redisson 客户端
     */
    public RedisRunGate(RedissonClient redisson) {
        this.redisson = redisson;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AcquireResult tryAcquire(String tenantId, String taskId, int maxConcurrent) {
        String tid = TenantIds.require(tenantId);
        RLock lock = redisson.getLock(LOCK_PREFIX + tid);
        lock.lock();
        try {
            RMapCache<String, Boolean> running = redisson.getMapCache(KEY_PREFIX + tid);
            // 加锁后读 size，避免并发突破上限
            if (running.size() >= maxConcurrent) {
                return AcquireResult.rejected("tenant concurrency limit reached");
            }
            // TTL 防止实例宕机后永久占坑
            running.put(taskId, Boolean.TRUE, ENTRY_TTL_MINUTES, TimeUnit.MINUTES);
            return AcquireResult.ok();
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void release(String tenantId, String taskId) {
        String tid = TenantIds.require(tenantId);
        redisson.getMapCache(KEY_PREFIX + tid).remove(taskId);
    }
}
