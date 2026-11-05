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
 * 目录占位工具。
 * <p>所属模块：runtime。当某工具尚未挂上真实 MCP 客户端时，仍向模型暴露可调用入口并返回占位结果。
 */
public class CatalogStubTool implements AgentTool {

    private final String name;
    private final String description;
    private final boolean readOnly;

    /**
     * 用目录元数据构造占位工具。需要确认的工具视为非只读。
     *
     * @param tool 目录中的工具条目
     */
    public CatalogStubTool(CatalogTool tool) {
        this.name = sanitize(tool.name());
        this.description = tool.description() == null ? tool.name() : tool.description();
        // 需确认的工具按有副作用处理，避免模型把它当成只读查询
        this.readOnly = !tool.requireConfirm();
    }

    /**
     * 把工具名规范成 AgentScope 可注册的标识：点、横线与空格改为下划线。
     *
     * @param raw 原始工具名
     * @return 规范化名称
     */
    static String sanitize(String raw) {
        return raw.replace('.', '_').replace('-', '_').replace(' ', '_');
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return description;
    }

    /**
     * {@inheritDoc}
     * <p>入参为自由文本 {@code input}，并允许额外字段，便于占位阶段不绑死 schema。
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * {@inheritDoc}
     * <p>不访问远端，返回说明尚未连接 MCP 的占位文本。
     */
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
        // 明确标成 stub，避免模型把占位结果当成真实业务数据
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
