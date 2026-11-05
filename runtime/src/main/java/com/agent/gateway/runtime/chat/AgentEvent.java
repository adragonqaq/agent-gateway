package com.agent.gateway.runtime.chat;

/**
 * Agent 运行过程中的一条对外事件。
 * <p>所属模块：runtime。类型包括 thinking、answer、tool、confirm、error、done。
 *
 * @param type 事件类型
 * @param data 事件载荷；无内容时为空串
 */
public record AgentEvent(String type, String data) {
}
