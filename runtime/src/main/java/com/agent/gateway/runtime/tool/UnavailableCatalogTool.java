package com.agent.gateway.runtime.tool;

import com.agent.gateway.tools.CatalogTool;
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
 * MCP 不可用时的降级工具。
 * <p>所属模块：runtime。仍暴露给模型，但调用一律返回 ERROR，避免整轮因未知工具失败。
 */
public class UnavailableCatalogTool implements AgentTool {

    private final String name;
    private final String description;

    /**
     * @param tool 目录中 available=false 的条目
     */
    public UnavailableCatalogTool(CatalogTool tool) {
        this.name = CatalogStubTool.sanitize(tool.name());
        this.description = tool.description() == null
                ? "MCP server unavailable"
                : tool.description();
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
                "properties", Map.of(),
                "required", List.of(),
                "additionalProperties", true);
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        String toolCallId = Optional.ofNullable(param)
                .map(ToolCallParam::getToolUseBlock)
                .map(ToolUseBlock::getId)
                .orElse(null);
        return Mono.just(ToolResultBlock.builder()
                .id(toolCallId)
                .name(name)
                .output(io.agentscope.core.message.TextBlock.builder()
                        .text("MCP server unavailable: " + name)
                        .build())
                .state(ToolResultState.ERROR)
                .build());
    }
}
