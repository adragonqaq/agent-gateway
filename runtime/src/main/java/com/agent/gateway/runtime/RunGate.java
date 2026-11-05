package com.agent.gateway.runtime;

public interface RunGate {

    record AcquireResult(boolean acquired, String reason) {
        public static AcquireResult ok() {
            return new AcquireResult(true, null);
        }

        public static AcquireResult rejected(String reason) {
            return new AcquireResult(false, reason);
        }
    }

    AcquireResult tryAcquire(String tenantId, String taskId, int maxConcurrent);

    void release(String tenantId, String taskId);
}
