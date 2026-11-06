package com.agent.gateway.runtime.chat;

import io.agentscope.core.model.ChatUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenUsageCounterTest {

    @Test
    void accumulatesAcrossModelCalls() {
        TokenUsageCounter counter = new TokenUsageCounter();
        assertFalse(counter.hasUsage());

        counter.add(ChatUsage.builder().inputTokens(10).outputTokens(5).build());
        counter.add(ChatUsage.builder().inputTokens(3).outputTokens(7).build());
        counter.add(null);

        assertTrue(counter.hasUsage());
        assertEquals(13, counter.promptTokens());
        assertEquals(12, counter.completionTokens());
        assertEquals(25, counter.totalTokens());
    }
}
