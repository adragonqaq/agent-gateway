package com.agent.gateway.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisRunGateTest {

    private RedisRunGate gate;
    private ConcurrentHashMap<String, Boolean> store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        store = new ConcurrentHashMap<>();
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        RMapCache<String, Boolean> map = mock(RMapCache.class);
        when(client.getLock(anyString())).thenReturn(lock);
        doReturn(map).when(client).getMapCache(anyString());
        when(map.size()).thenAnswer(inv -> store.size());
        doAnswer(inv -> {
            store.put(inv.getArgument(0), Boolean.TRUE);
            return null;
        }).when(map).put(anyString(), eq(Boolean.TRUE), anyLong(), eq(TimeUnit.MINUTES));
        doAnswer(inv -> store.remove(inv.getArgument(0))).when(map).remove(anyString());
        gate = new RedisRunGate(client);
    }

    @Test
    void rejectsWhenSaturatedThenAllowsAfterRelease() {
        assertTrue(gate.tryAcquire("tA", "a1", 1).acquired());
        assertFalse(gate.tryAcquire("tA", "a2", 1).acquired());
        gate.release("tA", "a1");
        assertTrue(gate.tryAcquire("tA", "a2", 1).acquired());
    }
}
