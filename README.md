# Agent Gateway

多租户 **ReAct Agent 网关**（Java）。面向「通用 MCP 工具调用」场景，**不做 RAG / 知识入库**。

- 包名：`com.agent.gateway`
- Maven `groupId`：`com.agent`
- 执行引擎：AgentScope ReAct（可选；默认 echo 便于离线测试）
- 设计文档：[`docs/specs/2026-11-05-react-agent-gateway-design.md`](docs/specs/2026-11-05-react-agent-gateway-design.md)

---

## 项目是做什么的

对外提供可部署的 Agent 服务：租户用 API Key 鉴权后，通过 HTTP/SSE 发起多轮对话；模型在 ReAct 循环里决定是否调用该租户注册的 MCP 工具；写操作可走人工确认；全程按 `tenant_id` 做逻辑隔离，并记录审计与用量。

与「知识库问答平台」不同：本项目不包含文档解析、向量检索、意图树 RAG。需要检索时，由租户自己挂 MCP 工具提供。

---

## 模块说明

采用多模块 Maven 单体，依赖方向大致为：

`bootstrap` → `gateway-api` → `runtime` →（`tools` / `memory` / `observe` / `tenant`）

### `tenant` — 租户与准入

**作用：** 多租户 SaaS 的身份与策略根基。

- 租户档案（状态、模型档位、凭据绑定、并发上限）
- API Key 签发与哈希校验（明文只在创建时返回一次）
- 请求级 `TenantContext`（tenantId / role / modelTier 等）
- 工具策略：`ALLOW` / `DENY`、是否需要人工确认

没有本模块，后续所有「按租户切开」都无法成立。

### `tools` — MCP 注册与工具目录

**作用：** 把「租户配置了哪些 MCP」变成「模型本轮能看到哪些工具」。

- `McpRegistry`：持久化每租户的 MCP Server（URL、加密 Header）
- `SecretBox`：AES-GCM 加密敏感 Header，避免明文落库/进日志
- `TenantMcpClientPool`：按租户懒连接 MCP（SSE / streamable HTTP）并发现工具
- `TenantToolCatalog`：结合策略引擎生成 `ResolvedCatalog`（含指纹，供 Agent 重建判断）

本模块不跑 ReAct 循环，只负责工具供给侧。

### `memory` — 会话与 Agent 状态

**作用：** 多轮对话的状态落盘，键必须是 `tenant_id + conversation_id`。

- 保存 / 加载 Agent 侧上下文快照
- 保证跨租户同 conversationId 互相读不到（隔离回归覆盖）

长期记忆、上下文压缩不在 MVP 范围，后续可在本模块扩展。

### `runtime` — 执行引擎与运行时门禁

**作用：** 真正「跑一轮 Agent」的地方。

- `AgentChatService`：编排 RunGate → 解析工具目录 → 调用 `AgentExecutor` → 审计/用量
- `EchoAgentExecutor`：默认模式，离线可测
- `AgentScopeAgentExecutor` + `ReActAgentProvider`：`mode=agentscope` 时接入真实 ReAct
- `CatalogToolkitFactory` / `ConfirmingMcpTool`：把 MCP 工具挂进 AgentScope Toolkit；需确认的工具走 ASK
- `RunGate`：租户级并发闸门（当前内存实现）
- `ConfirmGate`：写操作挂起 / 同意 / 拒绝 / 超时

### `observe` — 可观测与计量

**作用：** 事后能查「谁做了什么、花了多少」。

- 审计事件：确认、取消、对话完成等
- 用量事件：按租户累计（如 runs / tokens）

所有写入同样强制带 `tenant_id`。

### `gateway-api` — HTTP / SSE 接入层

**作用：** 对外协议与鉴权过滤器，不含业务核心算法。

- `ApiKeyAuthFilter`：解析 Bearer / X-Api-Key，绑定 `TenantContextHolder`
- 管理面：创建租户、签发 Key
- 数据面：`/v1/chat`（SSE）、取消任务、确认/拒绝

### `bootstrap` — 启动装配

**作用：** Spring Boot 唯一可执行入口。

- `GatewayApplication`
- `application.yaml` 主配置
- `db/schema.sql` 与启动时 schema 初始化
- 组装上面所有模块，不承载领域逻辑

---

## 本地快速启动

```bash
docker compose up -d          # Postgres（Redis 可选；MVP RunGate 为内存实现）
export GATEWAY_SECRET_MASTER_KEY=0123456789abcdef
mvn -pl bootstrap -am spring-boot:run
```

### Agent 模式

| 模式 | 含义 |
|---|---|
| `echo`（默认） | 不调大模型，返回可预测事件，适合单测/联调骨架 |
| `agentscope` | 真实 AgentScope ReAct + OpenAI 兼容模型 |

```bash
export GATEWAY_AGENT_MODE=agentscope
export GATEWAY_LLM_API_KEY=sk-...
export GATEWAY_LLM_BASE_URL=https://api.openai.com
export GATEWAY_LLM_MODEL=gpt-4o-mini
```

---

## 主要 API

| 接口 | 说明 |
|---|---|
| `POST /admin/tenants` | 创建租户（需 ADMIN Key） |
| `POST /admin/tenants/{id}/api-keys` | 签发 API Key（明文仅返回一次） |
| `GET /admin/tenants/{id}/mcp-servers` | 列出 ACTIVE 的 MCP Server |
| `POST /admin/tenants/{id}/mcp-servers` | 登记 / 更新 MCP Server（Header 加密入库） |
| `POST /admin/tenants/{id}/mcp-servers/{serverId}/disable` | 停用 MCP Server |
| `POST /admin/tenants/{id}/tool-policies` | 写入工具 ALLOW/DENY 与是否需确认 |
| `POST /v1/chat` | SSE 对话（`thinking` / `tool` / `confirm` / `answer` / `done`） |
| `POST /v1/runs/{taskId}/cancel` | 取消任务（跨租户返回 404） |
| `POST /v1/confirms/{id}/approve\|deny` | 人工确认 / 拒绝 |

鉴权：`Authorization: Bearer <sk_...>` 或 `X-Api-Key: <sk_...>`。

---

## 开发规范（强制）

本仓库启用 Cursor Rule：`.cursor/rules/java-comment-standards.mdc`。

**每个类、每个方法必须有中文 Javadoc；关键调用行必须有「为什么调用」的中文注释。**  
不满足注释规范的改动，视为未完成。

---

## 验证

```bash
mvn test
```

---

## 文档

- 设计：[`docs/specs/2026-11-05-react-agent-gateway-design.md`](docs/specs/2026-11-05-react-agent-gateway-design.md)
- 实施计划：[`docs/plans/2026-11-05-react-agent-gateway-mvp.md`](docs/plans/2026-11-05-react-agent-gateway-mvp.md)
