package com.nageoffer.ai.gateway.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRunGateTest {

    @Test
    void tenantBNotBlockedByTenantASaturation() {
        InMemoryRunGate gate = new InMemoryRunGate();
        for (int i = 0; i < 5; i++) {
            assertTrue(gate.tryAcquire("tA", "task-a-" + i, 5).acquired());
        }
        assertFalse(gate.tryAcquire("tA", "task-a-overflow", 5).acquired());
        assertTrue(gate.tryAcquire("tB", "task-b-1", 5).acquired());
    }
}
