package com.agent.gateway.mcp.config;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpServerConfigTest {

    @Test
    void rejectsMissingReadOnlyHint() {
        McpServerFeatures.SyncToolSpecification bad = McpServerFeatures.SyncToolSpecification.builder()
                .tool(Tool.builder().name("x").description("d").build())
                .callHandler((e, r) -> null)
                .build();
        assertThrows(IllegalStateException.class, () -> McpServerConfig.requireReadOnlyHint(List.of(bad)));
    }

    @Test
    void acceptsDeclaredHint() {
        ToolAnnotations readOnly = new ToolAnnotations(null, true, false, true, false, null);
        McpServerFeatures.SyncToolSpecification ok = McpServerFeatures.SyncToolSpecification.builder()
                .tool(Tool.builder().name("y").description("d").annotations(readOnly).build())
                .callHandler((e, r) -> null)
                .build();
        assertDoesNotThrow(() -> McpServerConfig.requireReadOnlyHint(List.of(ok)));
    }
}
