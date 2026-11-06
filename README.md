<p align="center">
  <img src="docs/assets/logo.jpg" alt="Agent Gateway Logo" width="160" />
</p>

<h1 align="center">Agent Gateway</h1>

<p align="center">
  多租户 <b>ReAct Agent 网关</b>（Java）· MCP 优先 · 不做 RAG
</p>

<p align="center">
  包名 <code>com.agent.gateway</code> · Maven <code>groupId: com.agent</code>
</p>

---

## 项目定位

对外提供可部署的 Agent 服务：租户用 API Key 鉴权后，通过 **HTTP/SSE** 发起多轮对话；模型在 **ReAct** 循环里决定是否调用该租户注册的 **MCP** 工具；写操作可走人工确认；全程按 `tenant_id` 逻辑隔离，并记录审计与用量。

与「知识库问答平台」不同：**本项目不做文档解析、向量检索、意图树 RAG**。需要检索时，由租户自行挂 MCP 工具提供。

设计文档：[docs/specs/2026-11-05-react-agent-gateway-design.md](docs/specs/2026-11-05-react-agent-gateway-design.md)

---

## 快速启动

### 前置

- JDK 17+
- Maven 3.9+
- Docker（用于 Postgres / Redis）

### 1. 起基础设施

```bash
cd /path/to/agent-gateway
docker compose up -d
# 起 Postgres(15432) + Redis(16379) + 演示 MCP(19099)
# 也可用 profile：--spring.profiles.active=docker
```

### 2. 环境变量

```bash
export GATEWAY_SECRET_MASTER_KEY=0123456789abcdef   # MCP Header 加密主密钥（≥16 字节）
export GATEWAY_ADMIN_API_KEY=sk_admin_local_dev     # 可选：启动种子 ADMIN Key
# 真实模型（可选，OpenAI 兼容；示例 DeepSeek）：
# export GATEWAY_AGENT_MODE=agentscope
# export GATEWAY_LLM_API_KEY=sk-...
# export GATEWAY_LLM_BASE_URL=https://api.deepseek.com
# export GATEWAY_LLM_MODEL=deepseek-chat
# export GATEWAY_LLM_ENDPOINT=/v1/chat/completions
# 多实例并发闸（可选）：
# export GATEWAY_RUN_GATE_TYPE=redis
# export GATEWAY_REDIS_ADDRESS=redis://127.0.0.1:6379
```

### 3. 启动

```bash
# 首次或依赖变更后先 install，再只对 bootstrap 跑 spring-boot（勿对父工程加 -am run）
mvn -DskipTests install
mvn -pl bootstrap spring-boot:run -Dspring-boot.run.profiles=docker
# 默认 http://localhost:8080
```

### 4. 最小联调（echo 模式）

```bash
# 用种子 ADMIN Key 创建业务租户并签发 RUNTIME Key
curl -s -X POST http://localhost:8080/admin/tenants \
  -H "Authorization: Bearer sk_admin_local_dev" \
  -H "Content-Type: application/json" \
  -d '{"name":"demo","modelTier":"standard","maxConcurrent":5}'

# 将返回的 tenantId 代入签发 Key（示例）
curl -s -X POST http://localhost:8080/admin/tenants/<tenantId>/api-keys \
  -H "Authorization: Bearer sk_admin_local_dev" \
  -H "Content-Type: application/json" \
  -d '{"role":"RUNTIME"}'

# SSE 对话（把 apiKey 换成上一步返回的明文）
curl -N -X POST http://localhost:8080/v1/chat \
  -H "Authorization: Bearer <apiKey>" \
  -H "Content-Type: application/json" \
  -d '{"message":"hello"}'
```

### 5. OpenAPI

启动后打开：http://localhost:8080/swagger-ui.html

观测查询（需 RUNTIME/ADMIN Key）：

```bash
curl -s http://localhost:8080/v1/observe/audit?limit=20 -H "Authorization: Bearer <apiKey>"
curl -s http://localhost:8080/v1/observe/usage -H "Authorization: Bearer <apiKey>"
```

### 6. 验证测试

```bash
mvn test
```

### 7. 冒烟脚本

```bash
# 另开终端，服务已启动后：
./scripts/smoke.sh

# 无 Docker 时可用 H2 profile：
# mvn -pl bootstrap -am spring-boot:run -Dspring-boot.run.profiles=h2 -Dspring-boot.run.mainClass=com.agent.gateway.GatewayApplication
```

### 8. 演示 MCP Server（真实工具联调）

仓库内置独立模块 `mcp-server`（streamable HTTP，默认 `http://127.0.0.1:19099/mcp`），提供：

| 工具 | 读写 | 用途 |
|---|---|---|
| `demo_ping` | 读 | 连通性 |
| `demo_weather` | 读 | 假天气（上海/北京/东京有固定文案） |
| `demo_ticket_submit` | 写 | 内存工单；会触发 ConfirmGate |

```bash
# 方式 A：compose 已包含 mcp-server 服务（推荐）
docker compose up -d --build mcp-server

# 方式 B：本机进程（勿加 -am，否则会在父 pom 上误跑 spring-boot:run）
mvn -pl mcp-server spring-boot:run

# gateway 已启动后登记（替换 tenantId），或一键冒烟：
TENANT_ID=<tenantId> ./scripts/register-demo-mcp.sh
# 完整 MCP 冒烟（agentscope + LLM 时会打天气/工单确认）：
# GATEWAY_AGENT_MODE=agentscope ./scripts/smoke-mcp.sh

# 可选：给 demo MCP 开 Bearer，验证 gateway Header 密文登记链路
# export MCP_AUTH_TOKEN=demo-secret
# MCP_AUTH_TOKEN=demo-secret mvn -pl mcp-server spring-boot:run
# TENANT_ID=... MCP_AUTH_TOKEN=demo-secret ./scripts/register-demo-mcp.sh
# （list 接口应见 hasHeaders=true；错误 token 时 discover 降级为 unavailable）

# 对话示例（agentscope + 真实 LLM）：
# 「上海天气怎么样」→ demo_weather（只读，默认免确认）
# 「直接调用 demo_ticket_submit，title=打印机坏了」→ event:confirm
# 批准：POST /v1/confirms/{confirmId}/approve
```

### 9. 生产提示

- 关闭 Swagger：`springdoc.api-docs.enabled=false`
- 更换默认 `GATEWAY_SECRET_MASTER_KEY` / `GATEWAY_ADMIN_API_KEY`
- 变更说明见 [CHANGELOG.md](CHANGELOG.md)

---

## 项目架构

<p align="center">
  <img src="docs/assets/architecture.jpg" alt="Agent Gateway Architecture" width="920" />
</p>

### 逻辑分层

| 层 | 模块 | 职责 |
|---|---|---|
| 接入层 | `gateway-api` | API Key 鉴权、管理面/数据面 HTTP、异步 SSE |
| 运行时 | `runtime` | ReAct 编排、ConfirmGate、RunGate、事件映射 |
| 领域支撑 | `tenant` / `tools` / `memory` / `observe` | 身份策略、MCP 目录、会话状态、审计用量 |
| 启动装配 | `bootstrap` | Spring Boot 入口、Schema 初始化、ADMIN 种子 |
| 演示 MCP | `mcp-server` | 独立进程；streamable HTTP `/mcp`，供登记联调 |
| 基础设施 | Postgres / Redis(可选) | 持久化；多实例并发闸 |

依赖方向（禁止反向依赖）：

```text
bootstrap → gateway-api → runtime → (tools | memory | observe | tenant)
tools / memory / observe → tenant
mcp-server（独立进程，不依赖 gateway 模块）
```

### 主链路（一次对话）

```text
Client
  → ApiKeyAuthFilter（绑定 TenantContext）
  → RunGate.tryAcquire
  → TenantToolCatalog.resolve（策略 + MCP 发现）
  → AgentExecutor（echo | agentscope）边跑边推 SSE
  → 若需确认：ConfirmGate.suspend → event:confirm
  → approve/deny → resumeConfirm（METADATA_CONFIRM_RESULTS）
  → SessionStateStore + Audit/Usage
  → RunGate.release
```

### 设计模式（本仓库实际用法）

| 模式 | 落点 | 作用 | 好处 | 后续如何扩展 |
|---|---|---|---|---|
| **分层 / 模块化** | Maven 多模块 + 单向依赖 | 把接入、运行时、租户/工具/记忆/观测拆开 | 边界清晰，改一处不易拖垮全局 | 新能力先落对应层；禁止 `tools` 反向依赖 `gateway-api` |
| **策略模式 Strategy** | `AgentExecutor`（`Echo` / `AgentScope`）；`RunGate`（`InMemory` / `Redis`） | 同一编排点可替换执行引擎或并发闸实现 | 本地 echo 与生产 agentscope、单机与多实例可切换 | 新增 `AgentExecutor` / `RunGate` 实现 + Spring 条件装配，不必改 `AgentChatService` 主流程 |
| **工厂模式 Factory** | `CatalogToolkitFactory` | 把目录 + 远端 MCP 工具装配成 AgentScope `Toolkit` | 注册规则集中，降级 stub / Confirm 工具统一入口 | 新工具形态（本地 Function、HTTP）在工厂内分支注册即可 |
| **注册表 Registry** | `McpRegistry` / `JdbcMcpRegistry`；`ActiveRunRegistry` | 持久化或内存登记「有哪些 MCP / 哪些在跑任务」 | 发现与取消有单一事实来源 | MCP 可加版本/健康字段；ActiveRun 可换分布式登记 |
| **对象池 / 连接缓存** | `TenantMcpClientPool` | 按租户缓存已 initialize 的 MCP 客户端 | 避免每轮对话重复握手 | 配变更时 `invalidate`；可加 TTL / 最大连接数 |
| **门禁 Gate** | `ConfirmGate`、`RunGate` | 写工具人工确认；租户并发限流 | 危险操作可审计可驳回；保护上游 LLM 配额 | Confirm 可接外部审批流；RunGate 可按 model_tier 差异化限额 |
| **过滤器 Filter + Context** | `ApiKeyAuthFilter` + `TenantContextHolder` | 请求入口鉴权并绑定租户线程上下文 | 下游无感获租户；强制 finally 清理防泄漏 | 可加 mTLS / OIDC，仍只写入同一 `TenantContext` |
| **仓储 / 端口思想** | `SessionStateStore`、`AgentStateStore` 等接口 + JDBC 实现 | 状态读写与存储引擎解耦 | 单测可换内存实现；生产用 JDBC | 加 Redis / 对象存储实现时只换 Bean |
| **装饰 / 包装** | `ConfirmingMcpTool`、`UnavailableCatalogTool` | 在真实 MCP 调用外包确认或降级错误 | 模型仍「看见」工具，行为由网关接管 | 可再包限流、重试、审计装饰器 |
| **模板方法式编排** | `AgentChatService` 主流程 | 抢闸 → 解析目录 → 执行 → 确认/取消 → 释放 | 横切关注点顺序固定 | 新步骤（配额预检、轨迹落盘）插到编排点，勿散落到 Controller |

确认策略补充：无 `tool-policies` 时按 MCP `readOnlyHint` 推导——只读默认免确认，写工具默认进 `ConfirmGate`；策略表可强制覆盖。

---

## 技术选型

| 类别 | 选型 | 说明 |
|---|---|---|
| 语言 / 运行时 | Java 17 | 与 AgentScope / Spring Boot 3 对齐 |
| 框架 | Spring Boot 3.4.x | Web、JDBC、配置绑定 |
| Agent 引擎 | AgentScope 2.0.2 | ReActAgent、Toolkit、权限 ASK、流式事件 |
| 工具协议 | MCP Java SDK 0.17 + AgentScope MCP 客户端 | 发现/调用远端 MCP |
| 数据库 | PostgreSQL 16 | 租户、MCP、确认单、审计等；逻辑多租户 |
| 缓存 / 闸门 | Redis 7 + Redisson（可选） | 多实例 RunGate；默认内存实现 |
| 构建 | Maven 多模块 | 清晰边界，便于按模块演进 |
| 测试 | JUnit 5 + H2（MODE=PostgreSQL） | 单测/隔离 IT；后续可加 Testcontainers |

**刻意不做：** 向量库、文档解析流水线、意图树 RAG（交给租户 MCP）。

---

## 前端（对话台 + 控制台）

独立仓库 [`agent-gateway-web`](../agent-gateway-web)（与本仓平级）：

- 设计：[docs/specs/2026-11-05-chat-console-design.md](docs/specs/2026-11-05-chat-console-design.md)、[用户体系](docs/specs/2026-11-06-user-project-auth-design.md)
- 邮箱密码注册/登录（HttpOnly JWT Cookie）+ 项目切换；对话不再依赖粘贴 API Key
- 顶栏「对话 | 控制台」：owner 可管配额 / MCP / 签发 RUNTIME Key；member 只读 MCP 与本人观测
- 本地：`npm run dev`（:5173），Vite 代理 `/v1` `/admin` 并携带 Cookie

---

## 模块说明

### `tenant` — 租户与准入

多租户 SaaS 的身份与策略根基：租户档案、API Key 签发与哈希校验、请求级 `TenantContext`、工具 `ALLOW/DENY` 与是否确认。

### `tools` — MCP 注册与工具目录

把「租户配置了哪些 MCP」变成「模型本轮能看到哪些工具」：`McpRegistry`、`SecretBox`、`TenantMcpClientPool`、`TenantToolCatalog`。

### `memory` — 会话与 Agent 状态

按 `tenant_id + conversation_id` 落盘快照，保证跨租户隔离。

### `runtime` — 执行引擎与门禁

`AgentChatService` 编排；`EchoAgentExecutor` / `AgentScopeAgentExecutor`；`ConfirmGate`；`InMemoryRunGate` / `RedisRunGate`。

### `observe` — 可观测与计量

审计事件与用量事件，写入强制带 `tenant_id`。

### `gateway-api` — HTTP / SSE

鉴权过滤器、管理面、聊天与确认 API；线程池异步边跑边推。

### `bootstrap` — 启动装配

`GatewayApplication`、Schema 初始化、ADMIN Key 种子、主配置。

---

## 主要 API

| 接口 | 说明 |
|---|---|
| `POST /admin/tenants` | 创建租户（ADMIN） |
| `POST /admin/tenants/{id}/api-keys` | 签发 API Key（明文仅一次） |
| `GET/POST /admin/tenants/{id}/mcp-servers` | 列出 / 登记 MCP |
| `POST /admin/tenants/{id}/mcp-servers/{serverId}/disable` | 停用 MCP |
| `POST /admin/tenants/{id}/tool-policies` | 工具策略 |
| `POST /v1/auth/register\|login\|refresh\|logout` | 用户会话（Cookie） |
| `GET /v1/auth/me` | 当前用户与项目列表 |
| `POST /v1/chat` | SSE 对话（异步边跑边推） |
| `POST /v1/runs/{taskId}/cancel` | 取消任务 |
| `POST /v1/confirms/{id}/approve\|deny` | 确认后续跑（SSE） |
| `GET /v1/projects/{id}/mcp-servers` | 成员只读 MCP 列表 |
| `GET /v1/observe/audit\|usage?scope=me\|project` | 观测（member 强制 me） |

鉴权：机器用 `Authorization: Bearer sk_…` / `X-Api-Key`；浏览器用 Cookie JWT + `X-Project-Id`。项目 owner 亦可调 `/admin/tenants/{id}/*`（配额、MCP、签发 RUNTIME Key）。

SSE `confirm` 事件示例：

```json
{
  "taskId": "task_xxx",
  "conversationId": "conv_xxx",
  "calls": [
    { "confirmId": "cfm_xxx", "name": "delete_order", "argsJson": "{}" }
  ]
}
```

---

## 数据模型摘要

详见 [`bootstrap/src/main/resources/db/schema.sql`](bootstrap/src/main/resources/db/schema.sql)（表/字段均有中文备注）。

| 表 | 含义 |
|---|---|
| `gw_tenant` | 租户 |
| `gw_api_key` | API Key（只存哈希） |
| `gw_mcp_server` | MCP Server（Header 密文） |
| `gw_tool_policy` | 工具策略 |
| `gw_conversation` | 会话元数据 |
| `gw_agent_state` | Agent 状态快照 |
| `gw_confirm` | 人工确认单 |
| `gw_audit_event` | 审计 |
| `gw_usage_event` | 用量 |

---

## 开发规范（强制）

Cursor Rules（不满足视为未完成）：

| Rule | 约束 |
|---|---|
| [`.cursor/rules/java-comment-standards.mdc`](.cursor/rules/java-comment-standards.mdc) | 类/方法中文 Javadoc；关键调用行写「为什么」 |
| [`.cursor/rules/config-comment-standards.mdc`](.cursor/rules/config-comment-standards.mdc) | YAML/Properties **每个配置键**必须有中文注释 |
| [`.cursor/rules/db-comment-standards.mdc`](.cursor/rules/db-comment-standards.mdc) | SQL 必须 `COMMENT ON TABLE` + 每个字段 `COMMENT ON COLUMN` |

补充：

- 行尾 `--` 备注**不能**代替 `COMMENT ON`（库表元数据以 `COMMENT ON` 为准）
- 敏感配置只通过环境变量注入，禁止把生产密钥写入仓库

---

## 配置要点

主配置：[`bootstrap/src/main/resources/application.yaml`](bootstrap/src/main/resources/application.yaml)（已逐项中文备注）。

| 环境变量 | 作用 |
|---|---|
| `GATEWAY_SECRET_MASTER_KEY` | MCP Header 加密主密钥 |
| `GATEWAY_ADMIN_API_KEY` | 启动种子 ADMIN Key |
| `GATEWAY_AGENT_MODE` | `echo` / `agentscope` |
| `GATEWAY_LLM_*` | 模型 API |
| `GATEWAY_RUN_GATE_TYPE` | `memory` / `redis` |
| `GATEWAY_REDIS_ADDRESS` | Redis 地址 |

---

## 文档与进度

- 设计：[`docs/specs/2026-11-05-react-agent-gateway-design.md`](docs/specs/2026-11-05-react-agent-gateway-design.md)
- 计划：[`docs/plans/2026-11-05-react-agent-gateway-mvp.md`](docs/plans/2026-11-05-react-agent-gateway-mvp.md)
- Logo / 架构图：[`docs/assets/`](docs/assets/)
