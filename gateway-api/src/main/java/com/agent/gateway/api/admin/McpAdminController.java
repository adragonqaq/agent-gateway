package com.agent.gateway.api.admin;

import com.agent.gateway.tenant.PolicyEngine;
import com.agent.gateway.tenant.ToolPolicy;
import com.agent.gateway.tools.McpAdminService;
import com.agent.gateway.tools.McpServerRecord;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Server 与工具策略管理面 API。
 * 仅 ADMIN 角色可调用；所有路径均显式带 tenantId，防止跨租户误操作。
 */
@RestController
@RequestMapping("/admin/tenants/{tenantId}")
public class McpAdminController {

    private final McpAdminService mcpAdminService;
    private final PolicyEngine policyEngine;

    /**
     * @param mcpAdminService MCP 登记服务
     * @param policyEngine 工具策略引擎
     */
    public McpAdminController(McpAdminService mcpAdminService, PolicyEngine policyEngine) {
        this.mcpAdminService = mcpAdminService;
        this.policyEngine = policyEngine;
    }

    /**
     * 列出租户 ACTIVE 的 MCP Server。
     *
     * @param tenantId 目标租户 ID
     * @return MCP Server 对外视图列表
     */
    @GetMapping("/mcp-servers")
    public List<Map<String, Object>> listMcpServers(@PathVariable String tenantId) {
        AdminAccess.requireAdminOrProjectOwner(tenantId);
        return mcpAdminService.listActive(tenantId).stream().map(this::toView).toList();
    }

    /**
     * 登记或更新 MCP Server。
     *
     * @param tenantId 目标租户 ID
     * @param request 登记/更新请求体
     * @return 持久化后的对外视图
     */
    @PostMapping("/mcp-servers")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> upsertMcpServer(
            @PathVariable String tenantId,
            @RequestBody UpsertMcpServerRequest request) {
        AdminAccess.requireAdminOrProjectOwner(tenantId);
        // 通过服务层加密 Header 并失效连接池，Controller 只做协议映射
        McpServerRecord saved = mcpAdminService.upsert(
                tenantId,
                request.id(),
                request.name(),
                request.transportUrl(),
                request.headers(),
                request.status());
        return toView(saved);
    }

    /**
     * 停用 MCP Server（不物理删除，便于审计追溯）。
     *
     * @param tenantId 目标租户 ID
     * @param serverId MCP Server ID
     * @return 停用后的对外视图
     */
    @PostMapping("/mcp-servers/{serverId}/disable")
    public Map<String, Object> disableMcpServer(
            @PathVariable String tenantId,
            @PathVariable String serverId) {
        AdminAccess.requireAdminOrProjectOwner(tenantId);
        return toView(mcpAdminService.disable(tenantId, serverId));
    }

    /**
     * 写入或更新单条工具策略（ALLOW/DENY + 是否确认）。
     *
     * @param tenantId 目标租户 ID
     * @param request 策略请求体
     * @return 写入后的策略摘要
     */
    @PostMapping("/tool-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> upsertToolPolicy(
            @PathVariable String tenantId,
            @RequestBody UpsertToolPolicyRequest request) {
        AdminAccess.requireAdminOrProjectOwner(tenantId);
        if (request.toolName() == null || request.toolName().isBlank()) {
            throw new IllegalArgumentException("toolName required");
        }
        String effect = request.effect() == null || request.effect().isBlank() ? "ALLOW" : request.effect();
        boolean confirm = request.requireConfirm() == null || request.requireConfirm();
        ToolPolicy policy = new ToolPolicy(request.toolName().trim(), effect.trim().toUpperCase(), confirm);
        // 策略按 tenant_id + tool_name 落库，运行时 resolve 目录会读取
        policyEngine.upsert(tenantId, policy);
        return Map.of(
                "toolName", policy.toolName(),
                "effect", policy.effect(),
                "requireConfirm", policy.requireConfirm());
    }

    /**
     * 将持久化记录转为对外视图（永不返回密文/明文 Header）。
     *
     * @param record MCP Server 持久化记录
     * @return 对外安全视图
     */
    private Map<String, Object> toView(McpServerRecord record) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", record.id());
        view.put("tenantId", record.tenantId());
        view.put("name", record.name());
        view.put("transportUrl", record.transportUrl());
        view.put("status", record.status());
        view.put("hasHeaders", record.headersCipher() != null && !record.headersCipher().isBlank());
        return view;
    }

}
