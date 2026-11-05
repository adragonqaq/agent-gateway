package com.agent.gateway.api.chat;

public record ChatRequest(String conversationId, String message) {
}
