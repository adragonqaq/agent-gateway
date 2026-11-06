package com.agent.gateway.api.project;

import com.agent.gateway.api.admin.AdminAccess;
import com.agent.gateway.tools.McpAdminService;
import com.agent.gateway.tools.McpServerRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目成员只读 MCP 列表（不含 Header 密文）。
 */
@RestController
@RequestMapping("/v1/projects/{projectId}")
public class ProjectMcpController {

    private final McpAdminService mcpAdminService;

    public ProjectMcpController(McpAdminService mcpAdminService) {
        this.mcpAdminService = mcpAdminService;
    }

    /**
     * 列出项目 ACTIVE MCP Server（member+）。
     */
    @GetMapping("/mcp-servers")
    public List<Map<String, Object>> listMcpServers(@PathVariable String projectId) {
        AdminAccess.requireProjectMember(projectId);
        return mcpAdminService.listActive(projectId).stream().map(this::toView).toList();
    }

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
