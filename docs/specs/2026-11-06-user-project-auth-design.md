# 用户体系 + 项目成员 + 对话台/控制台会话鉴权

日期：2026-11-06  
状态：待审  
范围：agent-gateway（后端）+ agent-gateway-web（同仓双模式前端）  
前置：全链路 Trace、本轮 token、侧栏观测、项目 token 配额已落地

## 背景与问题

当前只有 **API Key → 租户**，没有用户账号。对话台靠粘贴 RUNTIME Key，控制台/观测也按租户一把梭。

目标分层：

- **User**：登录对话台 / 控制台的人  
- **Tenant（项目）**：配额、MCP、数据隔离边界（沿用 `gw_tenant`）  
- **Membership**：用户在项目中的角色  
- **API Key**：保留给机器调用，不再作为浏览器主路径

## 已定决策

| 项 | 选择 |
|----|------|
| 仓库 | 不新开管理端项目；同仓 `agent-gateway-web` 双模式 |
| 登录 | 邮箱 + 密码 |
| 浏览器鉴权 | 用户会话直接调业务 API（Cookie + 当前项目） |
| 角色 | 两档：`owner` / `member` |
| 观测 | member 看自己的用量/审计；owner 看全员明细+汇总 |
| MCP | member **只读可见**；owner 可登记/禁用/策略 |
| 机器路径 | Bearer API Key 保留 |

## 1. 数据模型

### 新增

**`gw_user`**

| 列 | 说明 |
|----|------|
| id | `usr_…` |
| email | 唯一，小写规范化 |
| password_hash | 单向哈希（如 bcrypt/argon2） |
| status | ACTIVE / DISABLED |
| created_at | |

**`gw_session`**（不透明 session；若改 JWT 可省略表，本设计默认服务端 session）

| 列 | 说明 |
|----|------|
| id | `ses_…` |
| user_id | FK |
| token_hash | Cookie 中明文 token 的哈希 |
| expires_at | |
| created_at | |
| revoked_at | 可空 |

**`gw_project_member`**

| 列 | 说明 |
|----|------|
| tenant_id | 项目 = 现有 tenant |
| user_id | |
| role | `owner` \| `member` |
| created_at | |
| PK | `(tenant_id, user_id)` |

### 扩展

- `gw_usage_event.user_id` VARCHAR 可空  
- `gw_audit_event.user_id` VARCHAR 可空  
- 会话调用必填；API Key 调用保持 null  

### 注册流

1. 创建 `gw_user`  
2. 创建 `gw_tenant`（个人默认项目）  
3. 插入 `gw_project_member(role=owner)`  
4. 建立 session 并 Set-Cookie  

## 2. 鉴权与 API

### 浏览器

- `POST /v1/auth/register` `{email,password}`  
- `POST /v1/auth/login` → HttpOnly Cookie（`SameSite=Lax`；Secure 按环境）  
- `POST /v1/auth/logout`  
- `GET /v1/auth/me` → 用户 + 项目列表（含 role）  

业务请求：

- Cookie 会话  
- Header **`X-Project-Id: ten_…`**  
- 过滤器：校验 session → 校验 membership → 绑定  

```text
RequestContext {
  userId,      // 会话路径非空；API Key 路径 null
  tenantId,    // 项目
  role,        // owner | member | 或 API Key 的 ADMIN/RUNTIME
  authKind     // SESSION | API_KEY
}
```

与现有 `TenantContext` 衔接：会话路径同样填充 tenantId；增加 userId/role/authKind（可扩展 record 或并列 UserContext）。

### 机器调用

- `Authorization: Bearer sk_…` 行为不变  
- `userId = null`  

### 业务 API 行为变化

| API | 变化 |
|-----|------|
| `/v1/chat`、confirm、cancel | 接受 SESSION 或 API_KEY；SESSION 时 span/usage/audit 写 `user_id` |
| `/v1/observe/usage`、`/audit` | member 默认仅自己；owner 默认可看项目，或 `?scope=me\|project` |
| `/v1/observe/quota` | 项目配额；member 另可见「自己已用」 |
| `GET /v1/projects/{id}/mcp-servers` | 成员只读列表（新） |
| `/admin/tenants/{id}/mcp-servers*` 等 | 需 SESSION+owner（或保留平台 ADMIN Key） |
| `PUT /admin/tenants/{id}/quota` | SESSION+owner 或 ADMIN Key |

### 配额

- 仍挂在项目 `token_quota`  
- 开跑前校验项目累计 `total_tokens`  
- 观测：member 展示自己占用 vs 项目上限；owner 展示全员占用 vs 上限  

## 3. 前端（agent-gateway-web）

### 路由/模式

- `/login`、`/register`  
- 登录后顶栏：**对话 | 控制台** + **当前项目**下拉  
- 不新开仓库  

### 对话模式

- 去掉主路径「粘贴 RUNTIME Key」（可保留折叠「调试」）  
- 请求带 Cookie + `X-Project-Id`  
- 侧栏观测：  
  - member：我的用量/审计、配额占用、MCP 只读  
  - owner：可切换「我的 | 全项目」  
- Trace / 本轮 token 保持  

### 控制台模式

- owner 完整能力；member 只读或无权限提示  
- 本轮页面：  
  1. 概览（配额 + 全员汇总）  
  2. 成员列表（邀请二期）  
  3. MCP 登记/禁用/策略  
  4. 可选：签发 RUNTIME Key 给机器  

### 本地状态

- `projectId` 存 localStorage，与 `/me` 校验  
- 会话列表按 `projectId` 分区  
- 401 → 登录页  

## 4. 非目标（本轮不做）

- 邮箱验证、找回密码、OAuth  
- 邀请链接 / 多人邀请流（role 预留即可）  
- 平台级多租户超管 UI  
- 服务端会话历史按用户拉取（继续 localStorage）  
- OpenTelemetry 导出  

## 5. 迁移

- 现有 tenant / key / usage 数据保留  
- `user_id` 可空，旧行无归属  
- 本地用注册开新项目；旧 tenant 绑 owner 用脚本可选  
- bootstrap ADMIN Key 仍可用于运维 curl  

## 6. 验收

1. 注册 → 自动项目 + owner → Cookie 登录有效  
2. 对话不贴 Key 可聊；usage/audit 有 `user_id`；侧栏见「我的」  
3. owner 控制台可改配额、挂/禁 MCP；member 只读 MCP、不能改  
4. Bearer API Key 仍可调 `/v1/chat`  
5. 项目 token 超额仍 429  

## 7. 实现分期建议

1. Schema + User/Session/Membership + auth API + 过滤器双通道  
2. chat/observe 写读 `user_id` 与 scope  
3. 前端登录/注册/项目切换 + 对话去 Key  
4. 控制台概览 / MCP / 配额  
5. 成员只读 MCP API + 观测权限差  

---

审阅要点：会话用 DB session 是否接受；`TenantContext` 扩展方式；控制台是否本轮包含「签发 Key」页。
