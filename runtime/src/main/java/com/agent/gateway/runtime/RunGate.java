package com.agent.gateway.runtime;

/**
 * 租户任务并发闸门。
 * <p>所属模块：runtime。在聊天运行前后 acquire/release，防止单租户打满资源。
 */
public interface RunGate {

    /**
     * 尝试占用一次并发名额的结果。
     *
     * @param acquired 是否占用成功
     * @param reason 失败原因；成功时为 null
     */
    record AcquireResult(boolean acquired, String reason) {

        /**
         * @return 占用成功结果
         */
        public static AcquireResult ok() {
            return new AcquireResult(true, null);
        }

        /**
         * @param reason 拒绝原因
         * @return 占用失败结果
         */
        public static AcquireResult rejected(String reason) {
            return new AcquireResult(false, reason);
        }
    }

    /**
     * 尝试为租户任务占用一个并发槽位。
     *
     * @param tenantId 租户 ID
     * @param taskId 任务 ID
     * @param maxConcurrent 租户最大并发
     * @return 占用结果
     */
    AcquireResult tryAcquire(String tenantId, String taskId, int maxConcurrent);

    /**
     * 释放租户任务占用的并发槽位。
     *
     * @param tenantId 租户 ID
     * @param taskId 任务 ID
     */
    void release(String tenantId, String taskId);
}
