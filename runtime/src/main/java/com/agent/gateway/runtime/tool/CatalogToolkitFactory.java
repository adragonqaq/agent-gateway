package com.agent.gateway.runtime.tool;

import com.agent.gateway.tools.CatalogTool;
import com.agent.gateway.tools.ResolvedCatalog;
import com.agent.gateway.tools.mcp.RemoteMcpTool;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpTool;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按租户目录组装 AgentScope Toolkit。
 * <p>所属模块：runtime。优先注册真实 MCP 工具；不可用 Server 注册降级 ERROR 工具；其余用占位补齐。
 */
@Component
public class CatalogToolkitFactory {

    /**
     * 仅按目录构建 Toolkit，不挂远端 MCP 工具。
     *
     * @param catalog 已解析目录
     * @return 工具集
     */
    public Toolkit build(ResolvedCatalog catalog) {
        return build(catalog, List.of());
    }

    /**
     * 用远端 MCP 工具与目录共同构建 Toolkit。
     *
     * @param catalog 已解析目录，决定可见性与确认策略的来源之一
     * @param remotes 已发现的远端工具；可为空
     * @return 已注册工具的 Toolkit
     */
    public Toolkit build(ResolvedCatalog catalog, List<RemoteMcpTool> remotes) {
        Toolkit toolkit = new Toolkit();
        Map<String, RemoteMcpTool> byName = new HashMap<>();
        for (RemoteMcpTool remote : remotes) {
            byName.put(sanitize(remote.definition().name()), remote);
        }

        Set<String> registered = toolkit.getToolNames();
        for (RemoteMcpTool remote : remotes) {
            McpSchema.Tool def = remote.definition();
            String name = sanitize(def.name());
            if (registered.contains(name)) {
                // 同名工具只注册一次，避免 Agent 看到重复函数
                continue;
            }
            Boolean readOnlyHint = def.annotations() == null ? null : def.annotations().readOnlyHint();
            boolean readOnly = Boolean.TRUE.equals(readOnlyHint);
            // 确认策略以目录为准（含 tool-policies）；目录未命中时：显式 requireConfirm 或非只读
            boolean needsConfirm = catalog.tools().stream()
                    .filter(t -> sanitize(t.name()).equals(name))
                    .map(CatalogTool::requireConfirm)
                    .findFirst()
                    .orElseGet(() -> remote.requireConfirm() || !readOnly);
            Map<String, Object> params = McpTool.convertMcpSchemaToParameters(def.inputSchema(), Set.of());
            toolkit.registerAgentTool(ConfirmingMcpTool.from(
                    name,
                    def.description() == null ? name : def.description(),
                    params,
                    def.outputSchema(),
                    remote.client(),
                    remote.serverName(),
                    readOnly,
                    needsConfirm));
        }

        for (CatalogTool tool : catalog.tools()) {
            String name = CatalogStubTool.sanitize(tool.name());
            if (toolkit.getToolNames().contains(name) || byName.containsKey(name)) {
                // 已有真实 MCP 绑定则不再塞占位/降级实现
                continue;
            }
            if (!tool.available()) {
                // 降级工具仍注册，模型可见但调用返回 ERROR，符合「不中断整轮」
                toolkit.registerAgentTool(new UnavailableCatalogTool(tool));
                continue;
            }
            toolkit.registerAgentTool(new CatalogStubTool(tool));
        }
        return toolkit;
    }

    /**
     * 与 {@link CatalogStubTool#sanitize(String)} 保持同一套命名，保证远端名与占位名可对齐。
     *
     * @param raw 原始工具名
     * @return 规范化名称
     */
    private static String sanitize(String raw) {
        return CatalogStubTool.sanitize(raw);
    }
}
