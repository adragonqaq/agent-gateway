package com.agent.gateway.api.chat;

/**
 * 对话请求体。
 *
 * @param conversationId 会话 ID；为空时由服务端生成
 * @param message 用户本轮输入
 */
public record ChatRequest(String conversationId, String message) {
}
