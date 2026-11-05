package com.nageoffer.ai.gateway.memory;

import java.util.Optional;

public interface SessionStateStore {

    void save(String tenantId, String conversationId, String payload);

    Optional<String> load(String tenantId, String conversationId);
}
