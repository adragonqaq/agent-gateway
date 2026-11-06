# User / Project Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 浏览器用邮箱密码 + HttpOnly JWT 登录；项目（tenant）成员 owner/member；对话台/控制台同仓双模式，机器仍可用 API Key。

**Architecture:** 扩展 `tenant`/`gateway-api`：用户与成员表、JWT 签发校验、过滤器双通道（JWT Cookie | Bearer Key）。`observe`/`runtime` 写入可读 `user_id` 与 scope。`agent-gateway-web` 增加登录与控制台模式。

**Tech Stack:** Spring Boot 3.4、JJWT、BCrypt、现有 JDBC schema、Vite React。

**Spec:** `docs/specs/2026-11-06-user-project-auth-design.md`

---

## File map

| Area | Create / Modify |
|------|-----------------|
| Schema | `bootstrap/.../db/schema.sql` + 各 `schema-h2.sql` |
| Tenant | `UserRecord`, `MembershipRecord`, repos, `PasswordHasher`, `JwtService`, `AuthService` |
| API | `AuthController`, extend `ApiKeyAuthFilter` → dual auth filter, extend `TenantContext` |
| Observe | `user_id` on usage/audit; list filters by scope |
| Runtime | pass `userId` into audit/usage on chat |
| Web | `LoginPage`, `RegisterPage`, project switcher, Console views, credentials: `include` |

---

### Task 1: Schema — user / member / user_id columns

**Files:**
- Modify: `bootstrap/src/main/resources/db/schema.sql`
- Modify: all module `schema-h2.sql`

- [ ] Add `gw_user`, `gw_project_member`
- [ ] `ALTER` / columns: `gw_usage_event.user_id`, `gw_audit_event.user_id`
- [ ] Commit

### Task 2: Tenant — user + membership + password + JWT

**Files:**
- Create under `tenant/src/main/java/.../tenant/auth/` or `tenant/`:
  - `UserRecord`, `JdbcUserRepository`
  - `ProjectMemberRecord`, `JdbcProjectMemberRepository`
  - `PasswordHasher` (BCrypt)
  - `JwtService` (HS256 access/refresh)
  - `AuthService` (register/login/me)
- Modify: `tenant/pom.xml` — jjwt + spring-security-crypto
- Test: register hashes password; jwt round-trip; membership lookup

- [ ] Failing tests → implement → green → commit

### Task 3: Auth API + dual filter

**Files:**
- Create: `gateway-api/.../auth/AuthController.java`
- Modify: `ApiKeyAuthFilter` (or new `RequestAuthFilter`) — Cookie JWT **or** Bearer Key
- Modify: `TenantContext` — add `userId`, `memberRole`, `authKind` (compat constructors for tests)
- Config: `gateway.auth.jwt-secret`, cookie names, TTLs in `application.yaml`
- Cookie: `Set-Cookie` on login/register/refresh; clear on logout
- Require `X-Project-Id` for JWT path on `/v1/**` business routes (except `/v1/auth/**`)

- [ ] MVC tests: register → me；JWT chat path mock；Key path still works → commit

### Task 4: Observe + Runtime user_id / scope

**Files:**
- Modify: `AuditService.record` / `UsageService.record` — optional `userId`
- Modify: `AgentChatService` — pass userId from context when present
- Modify: observe list APIs — `scope=me|project`; member forced to `me`
- Modify: quota snapshot — include `myTokenUsed` when userId present

- [ ] Isolation tests with user_id → commit

### Task 5: Project MCP read-only + owner admin via session

**Files:**
- Create: `GET /v1/projects/{projectId}/mcp-servers` (member+)
- Modify: admin MCP/quota — allow JWT owner of that tenant (not only ADMIN key)

- [ ] Tests → commit

### Task 6: Web — auth + project switch + chat without Key

**Files:**
- `agent-gateway-web`: login/register pages, auth api (`credentials: 'include'`), store projectId
- App: gate on `/me`; drop required API Key; send `X-Project-Id`
- Observe panel: scope me/project by role

- [ ] Manual: register → chat → see tokens → commit

### Task 7: Web — Console mode

**Files:**
- Console: overview (quota/usage), MCP CRUD, issue API key, member list (read-only this round)
- Top nav: 对话 | 控制台

- [ ] Manual owner flows → commit

### Task 8: Push + smoke

- [ ] Push both repos; docker schema ALTER applied; README auth section

---

## Notes

- Do not remove API Key path.
- Prefer extending `TenantContext` over parallel ThreadLocals.
- Frontend Vite proxy must forward `Cookie` / `Set-Cookie`.
