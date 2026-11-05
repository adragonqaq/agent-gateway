package com.agent.gateway.mcp.tool;

import com.agent.gateway.mcp.config.McpToolAnnotations;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.agent.gateway.mcp.tool.McpToolSchema.string;

/**
 * 演示写工具：提交工单（内存落库）。
 * <p>{@code readOnlyHint=false}，gateway 侧会走 ConfirmGate，用于联调确认/续跑。
 */
@Component
public class DemoTicketSubmitTool {

    private static final Logger log = LoggerFactory.getLogger(DemoTicketSubmitTool.class);

    public static final String TOOL_ID = "demo_ticket_submit";

    private final ConcurrentHashMap<String, String> tickets = new ConcurrentHashMap<>();

    @Bean
    public McpServerFeatures.SyncToolSpecification demoTicketSubmitToolSpecification() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(buildTool())
                .callHandler((exchange, request) -> handle(request))
                .build();
    }

    private Tool buildTool() {
        JsonSchema schema = McpToolSchema.object()
                .required(string("title", "工单标题，简短描述问题"))
                .optional(string("detail", "可选详情说明"))
                .build();
        return Tool.builder()
                .name(TOOL_ID)
                .description("提交演示工单（会写入内存，有副作用）。用户明确要求建工单/提 ticket 时调用；调用前需人工确认。")
                .inputSchema(schema)
                .annotations(McpToolAnnotations.WRITE)
                .build();
    }

    CallToolResult handle(CallToolRequest request) {
        Map<String, Object> args = McpToolResults.args(request);
        Object titleRaw = args.get("title");
        if (titleRaw == null || String.valueOf(titleRaw).isBlank()) {
            return McpToolResults.error("title is required");
        }
        String title = String.valueOf(titleRaw).trim();
        Object detailRaw = args.get("detail");
        String detail = detailRaw == null ? "" : String.valueOf(detailRaw).trim();
        String ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        tickets.put(ticketId, title + (detail.isBlank() ? "" : " | " + detail));
        log.info("demo ticket created id={} title={}", ticketId, title);
        return McpToolResults.success(
                "工单已创建\n编号: " + ticketId + "\n标题: " + title
                        + (detail.isBlank() ? "" : "\n详情: " + detail));
    }
}
