package com.agent.gateway.mcp.tool;

import com.agent.gateway.mcp.config.McpToolAnnotations;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

import static com.agent.gateway.mcp.tool.McpToolSchema.string;

/**
 * 假天气查询：固定演示数据，不访问外网。
 */
@Component
public class DemoWeatherTool {

    public static final String TOOL_ID = "demo_weather";

    @Bean
    public McpServerFeatures.SyncToolSpecification demoWeatherToolSpecification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(buildTool())
                .callHandler((exchange, request) -> handle(request))
                .build();
    }

    private Tool buildTool() {
        JsonSchema schema = McpToolSchema.object()
                .required(string("city", "城市名，如 上海、Beijing、Tokyo"))
                .build();
        return Tool.builder()
                .name(TOOL_ID)
                .description("查询演示天气（假数据，不访问外网）。需要城市天气时调用本工具。")
                .inputSchema(schema)
                .annotations(McpToolAnnotations.READ_ONLY)
                .build();
    }

    CallToolResult handle(CallToolRequest request) {
        Map<String, Object> args = McpToolResults.args(request);
        Object raw = args.get("city");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return McpToolResults.error("city is required");
        }
        String city = String.valueOf(raw).trim();
        String key = city.toLowerCase(Locale.ROOT);
        String summary = switch (key) {
            case "上海", "shanghai" -> "晴，22°C，东南风 2 级";
            case "北京", "beijing" -> "多云，18°C，北风 3 级";
            case "东京", "tokyo" -> "小雨，16°C，东风 2 级";
            default -> "晴间多云，20°C（演示数据）";
        };
        return McpToolResults.success(city + "：" + summary);
    }
}
