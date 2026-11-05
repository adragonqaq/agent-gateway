package com.agent.gateway.runtime.tool;

import com.agent.gateway.tools.CatalogTool;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Temporary AgentTool backed by catalog metadata until MCP proxy is wired.
 */
public class CatalogStubTool implements AgentTool {

    private final String name;
    private final String description;
    private final boolean readOnly;

    public CatalogStubTool(CatalogTool tool) {
        this.name = sanitize(tool.name());
        this.description = tool.description() == null ? tool.name() : tool.description();
        this.readOnly = !tool.requireConfirm();
    }

    static String sanitize(String raw) {
        return raw.replace('.', '_').replace('-', '_').replace(' ', '_');
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "input", Map.of(
                                "type", "string",
                                "description", "Free-form tool input")),
                "required", List.of(),
                "additionalProperties", true);
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        String toolCallId = Optional.ofNullable(param)
                .map(ToolCallParam::getToolUseBlock)
                .map(ToolUseBlock::getId)
                .orElse(null);
        String input = Optional.ofNullable(param)
                .map(ToolCallParam::getInput)
                .map(Object::toString)
                .orElse("{}");
        String text = "stub://" + name + " executed with " + input
                + " (MCP proxy not connected yet)";
        return Mono.just(ToolResultBlock.builder()
                .id(toolCallId)
                .name(name)
                .output(TextBlock.builder().text(text).build())
                .state(ToolResultState.SUCCESS)
                .build());
    }
}
