package com.agent.gateway.runtime.chat;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 进行中聊天任务的注册表。
 * <p>所属模块：runtime。供 cancel 定位会话并触发 AgentScope interrupt，避免只释放闸门却不停模型。
 */
@Component
public class ActiveRunRegistry {

    private final Map<String, ActiveRun> runs = new ConcurrentHashMap<>();

    /**
     * 登记一次运行中的任务。
     *
     * @param taskId 任务 ID
     * @param tenantId 租户 ID
     * @param conversationId 会话 ID
     * @param cancelled 协作式取消标志（echo 等同步执行器可读）
     * @param interrupt 打断上游的动作（可为 no-op）
     */
    public void register(
            String taskId,
            String tenantId,
            String conversationId,
            AtomicBoolean cancelled,
            Runnable interrupt) {
        runs.put(taskId, new ActiveRun(tenantId, conversationId, cancelled, interrupt == null ? () -> {
        } : interrupt));
    }

    /**
     * 按任务 ID 查询。
     *
     * @param taskId 任务 ID
     * @return 活跃运行信息
     */
    public Optional<ActiveRun> find(String taskId) {
        return Optional.ofNullable(runs.get(taskId));
    }

    /**
     * 移除登记（任务结束或取消后调用）。
     *
     * @param taskId 任务 ID
     */
    public void remove(String taskId) {
        runs.remove(taskId);
    }

    /**
     * 一次活跃运行的快照。
     *
     * @param tenantId 租户 ID
     * @param conversationId 会话 ID
     * @param cancelled 是否已请求取消
     * @param interrupt 打断动作
     */
    public record ActiveRun(
            String tenantId,
            String conversationId,
            AtomicBoolean cancelled,
            Runnable interrupt
    ) {
        /**
         * 标记取消并执行打断；幂等。
         */
        public void requestCancel() {
            if (cancelled.compareAndSet(false, true)) {
                // 先置标志再 interrupt，避免执行器在标志未就绪时继续产出事件
                interrupt.run();
            }
        }
    }
}
