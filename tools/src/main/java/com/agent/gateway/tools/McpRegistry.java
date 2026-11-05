package com.agent.gateway.tools;

import java.util.List;
import java.util.Optional;

public interface McpRegistry {

    void upsert(McpServerRecord server);

    List<McpServerRecord> listActive(String tenantId);

    Optional<McpServerRecord> find(String tenantId, String serverId);
}
