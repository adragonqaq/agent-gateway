package com.agent.gateway.runtime.chat;

import io.agentscope.core.model.ChatUsage;

/**
 * 累加本轮多次模型调用的 token。
 * <p>所属模块：runtime。模型未回传 usage 时保持 {@link #hasUsage()} 为 false。
 */
final class TokenUsageCounter {

    private long promptTokens;
    private long completionTokens;
    private boolean hasUsage;

    /**
     * @param usage 单次模型调用用量；null 忽略
     */
    void add(ChatUsage usage) {
        if (usage == null) {
            return;
        }
        promptTokens += Math.max(0, usage.getInputTokens());
        completionTokens += Math.max(0, usage.getOutputTokens());
        hasUsage = true;
    }

    boolean hasUsage() {
        return hasUsage;
    }

    long promptTokens() {
        return promptTokens;
    }

    long completionTokens() {
        return completionTokens;
    }

    long totalTokens() {
        return promptTokens + completionTokens;
    }

    Integer promptTokensOrNull() {
        return hasUsage ? (int) Math.min(Integer.MAX_VALUE, promptTokens) : null;
    }

    Integer completionTokensOrNull() {
        return hasUsage ? (int) Math.min(Integer.MAX_VALUE, completionTokens) : null;
    }
}
