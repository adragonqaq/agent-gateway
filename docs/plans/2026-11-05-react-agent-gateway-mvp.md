# Agent Gateway MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a **new repository** `agent-gateway` and ship an MVP multi-tenant SaaS ReAct Agent service (HTTP/SSE + MCP tools + confirm gate + RunGate), without RAG.

**Architecture:** Control plane (tenant / API key / MCP registry / policy / quota) feeds a data plane (auth → RunGate → tenant tool catalog → AgentScope ReAct + middleware → SSE). Shared Postgres/Redis with `tenant_id` logical isolation. Spec: `docs/superpowers/specs/2026-11-05-react-agent-gateway-design.md` (copy into the new repo on Task 1).

**Tech Stack:** Java 17 · Spring Boot 3.4.x · AgentScope 2.0.2 · MCP Java SDK 0.17.0 · Postgres · Redis · JUnit 5 · Testcontainers

**Critical constraint:** All implementation work happens in the **new** repo (`../agent-gateway` next to `ragent`). Do **not** add gateway modules under `ragent/`.

**Resolved open items (for this plan):**

| Item | Decision |
|---|---|
| Repo name | `agent-gateway` |
| AuthN (MVP) | Data plane + control plane both use API keys (`sk_...`); OIDC later |
| Admin UI | API + OpenAPI only |
| Secrets | AES-GCM encrypted columns; master key from env `GATEWAY_SECRET_MASTER_KEY` |

---

## File structure (new repo)

```
agent-gateway/
├── pom.xml                          # parent BOM
├── README.md
├── docs/
│   └── specs/2026-11-05-react-agent-gateway-design.md
├── tenant/                          # tenants, keys, quotas, policies
├── tools/                           # MCP registry + tool catalog
├── memory/                          # conversation + agent state
├── runtime/                         # ReAct provider, run handle, confirm, run gate
├── observe/                         # audit + usage events
├── gateway-api/                     # HTTP/SSE controllers + filters
└── bootstrap/                       # Spring Boot main + application.yaml
```

Package root: `com.nageoffer.ai.gateway`.

Dependency direction (no cycles):

`bootstrap` → `gateway-api` → `runtime` → (`tools`, `memory`, `observe`, `tenant`)  
`tools` → `tenant`  
`memory` → `tenant`  
`observe` → `tenant`  
`runtime` → AgentScope

---

### Task 1: Create the new repository and parent POM

**Files:**
- Create: `/Users/liaozhilong/project/github/agent-gateway/` (sibling of `ragent`)
- Create: `pom.xml`, `README.md`, `.gitignore`, `docs/specs/2026-11-05-react-agent-gateway-design.md`

- [ ] **Step 1: Create the empty git repo next to ragent**

```bash
mkdir -p /Users/liaozhilong/project/github/agent-gateway
cd /Users/liaozhilong/project/github/agent-gateway
git init
```

- [ ] **Step 2: Add `.gitignore`**

```gitignore
target/
.idea/
*.iml
.classpath
.project
.settings/
.DS_Store
.env
.output/
.superpowers/
```

- [ ] **Step 3: Write parent `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.nageoffer.ai</groupId>
    <artifactId>agent-gateway</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>agent-gateway</name>
    <description>Multi-tenant ReAct Agent Gateway (MCP-first, no RAG)</description>

    <modules>
        <module>tenant</module>
        <module>tools</module>
        <module>memory</module>
        <module>observe</module>
        <module>runtime</module>
        <module>gateway-api</module>
        <module>bootstrap</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <spring-boot.version>3.4.5</spring-boot.version>
        <agentscope.version>2.0.2</agentscope.version>
        <mcp-sdk.version>0.17.0</mcp-sdk.version>
        <redisson.version>3.45.1</redisson.version>
        <testcontainers.version>1.20.6</testcontainers.version>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>io.agentscope</groupId>
                <artifactId>agentscope-core</artifactId>
                <version>${agentscope.version}</version>
            </dependency>
            <dependency>
                <groupId>io.agentscope</groupId>
                <artifactId>agentscope-extensions-model-openai</artifactId>
                <version>${agentscope.version}</version>
            </dependency>
            <dependency>
                <groupId>io.modelcontextprotocol.sdk</groupId>
                <artifactId>mcp</artifactId>
                <version>${mcp-sdk.version}</version>
            </dependency>
            <dependency>
                <groupId>org.redisson</groupId>
                <artifactId>redisson-spring-boot-starter</artifactId>
                <version>${redisson.version}</version>
            </dependency>
            <dependency>
                <groupId>com.nageoffer.ai</groupId>
                <artifactId>tenant</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.nageoffer.ai</groupId>
                <artifactId>tools</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.nageoffer.ai</groupId>
                <artifactId>memory</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.nageoffer.ai</groupId>
                <artifactId>observe</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.nageoffer.ai</groupId>
                <artifactId>runtime</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.nageoffer.ai</groupId>
                <artifactId>gateway-api</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.14.0</version>
                    <configuration>
                        <release>${maven.compiler.release}</release>
                        <parameters>true</parameters>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 4: Copy the approved design spec into the new repo**

```bash
mkdir -p docs/specs
cp /Users/liaozhilong/project/github/ragent/docs/superpowers/specs/2026-11-05-react-agent-gateway-design.md \
   docs/specs/
```

- [ ] **Step 5: Write a short `README.md`** stating purpose, modules, and that this is not Ragent/RAG.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "$(cat <<'EOF'
chore: scaffold agent-gateway parent repo

EOF
)"
```

---

### Task 2: Scaffold empty Maven modules + bootstrap app

**Files:**
- Create: `tenant/pom.xml`, `tools/pom.xml`, `memory/pom.xml`, `observe/pom.xml`, `runtime/pom.xml`, `gateway-api/pom.xml`, `bootstrap/pom.xml`
- Create: `bootstrap/src/main/java/com/nageoffer/ai/gateway/GatewayApplication.java`
- Create: `bootstrap/src/main/resources/application.yaml`

- [ ] **Step 1: Create each module `pom.xml`**

Minimal pattern (example `tenant/pom.xml`; others follow with their deps):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.nageoffer.ai</groupId>
        <artifactId>agent-gateway</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>tenant</artifactId>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Wiring notes:
- `tools` depends on `tenant` + `mcp` + `agentscope-core`
- `memory` depends on `tenant` + jdbc
- `observe` depends on `tenant` + jdbc
- `runtime` depends on `tools`, `memory`, `observe`, `agentscope-core`, `agentscope-extensions-model-openai`, `redisson-spring-boot-starter`
- `gateway-api` depends on `runtime` + `spring-boot-starter-web`
- `bootstrap` depends on `gateway-api` + spring-boot plugin

- [ ] **Step 2: Add bootstrap main class**

```java
package com.nageoffer.ai.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.nageoffer.ai.gateway")
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

- [ ] **Step 3: Add minimal `application.yaml`**

```yaml
server:
  port: 8080
spring:
  application:
    name: agent-gateway
  datasource:
    url: jdbc:postgresql://localhost:5432/agent_gateway
    username: gateway
    password: gateway
gateway:
  secret:
    master-key: ${GATEWAY_SECRET_MASTER_KEY:}
  run-gate:
    max-concurrent-per-tenant: 5
  confirm:
    timeout-seconds: 300
```

- [ ] **Step 4: Verify reactor builds**

```bash
cd /Users/liaozhilong/project/github/agent-gateway
mvn -q -DskipTests package
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "$(cat <<'EOF'
chore: add multi-module Maven skeleton and bootstrap

EOF
)"
```

---

### Task 3: Tenant context + repository isolation guard

**Files:**
- Create: `tenant/src/main/java/com/nageoffer/ai/gateway/tenant/TenantContext.java`
- Create: `tenant/src/main/java/com/nageoffer/ai/gateway/tenant/TenantIds.java`
- Create: `tenant/src/main/java/com/nageoffer/ai/gateway/tenant/MissingTenantException.java`
- Create: `tenant/src/test/java/com/nageoffer/ai/gateway/tenant/TenantIdsTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.nageoffer.ai.gateway.tenant;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TenantIdsTest {
    @Test
    void requireRejectsBlank() {
        assertThrows(MissingTenantException.class, () -> TenantIds.require(" "));
    }

    @Test
    void requireReturnsTrimmed() {
        assertEquals("t1", TenantIds.require(" t1 "));
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (class missing)**

```bash
mvn -pl tenant -Dtest=TenantIdsTest test
```

- [ ] **Step 3: Implement**

```java
package com.nageoffer.ai.gateway.tenant;

public final class TenantIds {
    private TenantIds() {}
    public static String require(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new MissingTenantException("tenant_id is required");
        }
        return tenantId.trim();
    }
}
```

```java
package com.nageoffer.ai.gateway.tenant;

public class MissingTenantException extends RuntimeException {
    public MissingTenantException(String message) { super(message); }
}
```

```java
package com.nageoffer.ai.gateway.tenant;

public record TenantContext(
        String tenantId,
        String apiKeyId,
        String modelTier,
        String credentialBinding
) {}
```

- [ ] **Step 4: Re-run tests — expect PASS**

```bash
mvn -pl tenant -Dtest=TenantIdsTest test
```

- [ ] **Step 5: Commit**

```bash
git add tenant
git commit -m "$(cat <<'EOF'
feat(tenant): add tenant id guard and context record

EOF
)"
```

---

### Task 4: Schema — tenants, api_keys, quotas

**Files:**
- Create: `bootstrap/src/main/resources/db/schema.sql`
- Create: `tenant/src/main/java/com/nageoffer/ai/gateway/tenant/TenantRecord.java`
- Create: `tenant/src/main/java/com/nageoffer/ai/gateway/tenant/ApiKeyRecord.java`
- Create: `tenant/src/main/java/com/nageoffer/ai/gateway/tenant/TenantRepository.java`
- Create: `tenant/src/main/java/com/nageoffer/ai/gateway/tenant/JdbcTenantRepository.java`
- Create: `tenant/src/test/java/com/nageoffer/ai/gateway/tenant/JdbcTenantRepositoryTest.java`

- [ ] **Step 1: Write schema**

```sql
CREATE TABLE IF NOT EXISTS gw_tenant (
    id              VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    model_tier      VARCHAR(64) NOT NULL DEFAULT 'standard',
    credential_binding VARCHAR(64) NOT NULL DEFAULT 'platform',
    max_concurrent  INT NOT NULL DEFAULT 5,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS gw_api_key (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL REFERENCES gw_tenant(id),
    key_hash        VARCHAR(128) NOT NULL UNIQUE,
    key_prefix      VARCHAR(16) NOT NULL,
    role            VARCHAR(32) NOT NULL DEFAULT 'RUNTIME',
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_gw_api_key_tenant ON gw_api_key(tenant_id);
```

- [ ] **Step 2: Write failing repository test** (use Testcontainers Postgres, or H2 only if you keep SQL dialect-compatible; prefer Testcontainers):

```java
@Test
void findByKeyHashRequiresTenantScopedRow() {
    repo.insertTenant(new TenantRecord("tA", "A", "ACTIVE", "standard", "platform", 5));
    repo.insertTenant(new TenantRecord("tB", "B", "ACTIVE", "standard", "platform", 5));
    repo.insertApiKey(new ApiKeyRecord("k1", "tA", "hashA", "sk_a", "RUNTIME", "ACTIVE"));
    assertTrue(repo.findActiveByKeyHash("hashA").isPresent());
    assertEquals("tA", repo.findActiveByKeyHash("hashA").get().tenantId());
}
```

- [ ] **Step 3: Run — expect FAIL**

```bash
mvn -pl tenant -Dtest=JdbcTenantRepositoryTest test
```

- [ ] **Step 4: Implement `JdbcTenantRepository`** with every SELECT including tenant predicates where applicable; `findActiveByKeyHash` joins `gw_tenant` and rejects non-ACTIVE.

- [ ] **Step 5: Tests PASS, then commit**

```bash
git add bootstrap/src/main/resources/db tenant
git commit -m "$(cat <<'EOF'
feat(tenant): add tenant and api_key schema with jdbc repository

EOF
)"
```

---

### Task 5: API key resolver + auth filter

**Files:**
- Create: `tenant/src/main/java/com/nageoffer/ai/gateway/tenant/ApiKeyHasher.java`
- Create: `tenant/src/main/java/com/nageoffer/ai/gateway/tenant/TenantResolver.java`
- Create: `gateway-api/src/main/java/com/nageoffer/ai/gateway/api/auth/ApiKeyAuthFilter.java`
- Create: `gateway-api/src/main/java/com/nageoffer/ai/gateway/api/auth/TenantContextHolder.java`
- Test: `tenant/src/test/java/com/nageoffer/ai/gateway/tenant/TenantResolverTest.java`

- [ ] **Step 1: Failing test — unknown key rejected**

```java
@Test
void unknownKeyThrows() {
    when(repo.findActiveByKeyHash(any())).thenReturn(Optional.empty());
    assertThrows(UnauthorizedException.class, () -> resolver.resolve("sk_bad"));
}
```

- [ ] **Step 2: Implement hasher (SHA-256 hex of raw key), resolver, and servlet filter** reading `Authorization: Bearer sk_...` or `X-Api-Key`, putting `TenantContext` into `TenantContextHolder` (ThreadLocal + clear in `finally`).

- [ ] **Step 3: Tests PASS + commit**

```bash
git commit -m "$(cat <<'EOF'
feat(auth): resolve tenant from API key and bind request context

EOF
)"
```

---

### Task 6: Control-plane APIs — create tenant + issue key

**Files:**
- Create: `gateway-api/.../admin/TenantAdminController.java`
- Create: request/response records under `gateway-api/.../admin/`
- Create: `tenant/.../TenantAdminService.java`
- Test: `@WebMvcTest` or slice test for create tenant

- [ ] **Step 1: Failing test** — `POST /admin/tenants` with platform admin key returns tenant id; `POST /admin/tenants/{id}/api-keys` returns raw key **once**.

- [ ] **Step 2: Implement services** — generate `sk_` + random; store only hash + prefix; require caller role `ADMIN` (seed one bootstrap admin key via SQL/fixture).

- [ ] **Step 3: PASS + commit**

```bash
git commit -m "$(cat <<'EOF'
feat(admin): create tenants and issue API keys

EOF
)"
```

---

### Task 7: MCP registry (per-tenant) + encrypted secrets

**Files:**
- Extend schema: `gw_mcp_server(id, tenant_id, name, transport_url, headers_cipher, status, updated_at)`
- Create: `tools/.../SecretBox.java` (AES-GCM)
- Create: `tools/.../McpServerRecord.java`, `McpRegistry.java`, `JdbcMcpRegistry.java`
- Test: `SecretBoxTest`, `JdbcMcpRegistryIsolationTest`

- [ ] **Step 1: Failing isolation test**

```java
@Test
void listForTenantNeverReturnsOtherTenantServers() {
    registry.upsert(server("tA", "weather"));
    registry.upsert(server("tB", "tickets"));
    assertEquals(1, registry.listActive("tA").size());
    assertEquals("weather", registry.listActive("tA").get(0).name());
}
```

- [ ] **Step 2: Implement `SecretBox`**

```java
public final class SecretBox {
    public String encrypt(String plaintext, byte[] masterKey) { /* AES-GCM, IV||ciphertext base64 */ }
    public String decrypt(String blob, byte[] masterKey) { /* ... */ }
}
```

Never log decrypted headers.

- [ ] **Step 3: PASS + commit**

```bash
git commit -m "$(cat <<'EOF'
feat(tools): per-tenant MCP registry with encrypted headers

EOF
)"
```

---

### Task 8: Tool catalog + policy engine

**Files:**
- Create: `tenant/.../PolicyEngine.java`, `ToolPolicy.java`
- Create: `tools/.../TenantToolCatalog.java`, `ResolvedCatalog.java`, `McpToolBinder.java`
- Schema: optional `gw_tool_policy(tenant_id, tool_name, effect ALLOW|DENY, require_confirm BOOLEAN)`
- Test: catalog filters denied tools; unreachable MCP marks unavailable without throwing

- [ ] **Step 1: Failing tests**

```java
@Test
void deniedToolExcludedFromCatalog() { ... }

@Test
void unreachableMcpMarkedUnavailable() { ... }
```

- [ ] **Step 2: Implement** — discover tools via AgentScope/MCP client per tenant server; apply policy; default `requireConfirm=true` for tools annotated destructive / or policy flag; build fingerprint from server ids + tool names + policy version.

- [ ] **Step 3: PASS + commit**

```bash
git commit -m "$(cat <<'EOF'
feat(tools): resolve tenant tool catalog with policy filtering

EOF
)"
```

---

### Task 9: Session + agent state store

**Files:**
- Schema: `gw_conversation(id, tenant_id, ...)`, `gw_agent_state(tenant_id, conversation_id, payload jsonb, updated_at)`
- Create: `memory/.../SessionStateStore.java`, `JdbcSessionStateStore.java`
- Test: cross-tenant get returns empty (not the other tenant’s payload)

- [ ] **Step 1: Failing isolation test**

```java
@Test
void loadOtherTenantStateIsEmpty() {
    store.save("tA", "c1", "{}");
    assertTrue(store.load("tB", "c1").isEmpty());
}
```

- [ ] **Step 2: Implement with composite key `tenant_id + conversation_id` always in WHERE.**

- [ ] **Step 3: PASS + commit**

```bash
git commit -m "$(cat <<'EOF'
feat(memory): tenant-scoped conversation agent state store

EOF
)"
```

---

### Task 10: RunGate (per-tenant concurrency)

**Files:**
- Create: `runtime/.../RunGate.java`, `RedisRunGate.java`
- Test: `RedisRunGateTest` with Testcontainers Redis (or embedded if acceptable)

- [ ] **Step 1: Failing test** — tenant at max concurrent rejects/queues; other tenant unaffected.

```java
@Test
void tenantBNotBlockedByTenantASaturation() {
    for (int i = 0; i < 5; i++) gate.tryAcquire("tA", "task-a-" + i);
    assertTrue(gate.tryAcquire("tB", "task-b-1").acquired());
}
```

- [ ] **Step 2: Implement Redis keys `gw:run:{tenantId}:{taskId}` + counter `gw:run-count:{tenantId}`; release in `finally`.

- [ ] **Step 3: PASS + commit**

```bash
git commit -m "$(cat <<'EOF'
feat(runtime): per-tenant run gate with isolated concurrency

EOF
)"
```

---

### Task 11: Confirm gate

**Files:**
- Create: `runtime/.../ConfirmGate.java`, `ConfirmDecision.java`, `PendingConfirmation.java`
- Create: Redis or DB table `gw_confirm(task_id, tenant_id, tool_name, args_json, status, expires_at)`
- Test: confirm / deny / timeout

- [ ] **Step 1: Failing tests**

```java
@Test
void timeoutBecomesDenied() { ... }

@Test
void otherTenantCannotConfirm() {
    String id = gate.suspend("tA", "task1", "delete_order", "{}");
    assertThrows(NotFoundException.class, () -> gate.confirm("tB", id));
}
```

- [ ] **Step 2: Implement suspend → SSE event payload; `POST /v1/runs/{taskId}/confirm` continues; deny writes audit.

- [ ] **Step 3: PASS + commit**

```bash
git commit -m "$(cat <<'EOF'
feat(runtime): confirm gate with tenant-safe confirm and timeout deny

EOF
)"
```

---

### Task 12: ReAct runtime wiring (AgentScope)

**Files:**
- Create: `runtime/.../ReActAgentProvider.java`
- Create: `runtime/.../AgentRunHandle.java`
- Create: `runtime/.../middleware/ConfirmDenialMiddleware.java`
- Create: `runtime/.../middleware/ToolBatchMiddleware.java`
- Create: `runtime/.../model/TenantChatModelFactory.java`
- Test: unit test provider rebuilds when catalog fingerprint changes; model factory refuses cross-tenant credential binding

- [ ] **Step 1: Failing tests for fingerprint rebuild + credential isolation**

- [ ] **Step 2: Implement provider**

```java
ReActAgent.builder()
    .name("AgentGateway")
    .sysPrompt(persona)
    .model(chatModelFor(tenant))
    .toolkit(catalog.toolkit())
    .maxIters(props.getMaxIters())
    .enablePendingToolRecovery(true)
    .stateStore(sessionStateStore.asAgentStateStore(tenantId, conversationId))
    .middleware(confirmDenialMiddleware)
    .middleware(toolBatchMiddleware)
    .build();
```

Use only credentials from `TenantContext.credentialBinding()` — never a global shared key for tenant-owned bindings.

- [ ] **Step 3: PASS + commit**

```bash
git commit -m "$(cat <<'EOF'
feat(runtime): wire AgentScope ReAct with tenant catalog and middlewares

EOF
)"
```

---

### Task 13: Chat SSE API + cancel

**Files:**
- Create: `gateway-api/.../chat/ChatController.java`
- Create: `gateway-api/.../chat/ChatRequest.java`
- Create: `runtime/.../AgentChatService.java`
- Create: `observe/.../AuditService.java`, `UsageService.java`
- Test: MockMvc/WebTestClient SSE smoke; cancel marks interrupted; cross-tenant stop returns 404

- [ ] **Step 1: Failing API tests**

```java
@Test
void chatRequiresApiKey() { ... }

@Test
void stopForeignTenantTaskReturns404() { ... }
```

- [ ] **Step 2: Implement flow** matching spec:

`API Key → TenantResolver → RunGate → load state → resolve catalog → ReAct stream → SSE events → save state → usage/audit`

SSE event names: `thinking`, `tool`, `confirm`, `answer`, `error`, `done`.

Cancel: interrupt + graceful save window; pending tool recovery on next turn via AgentScope flag.

- [ ] **Step 3: PASS + commit**

```bash
git commit -m "$(cat <<'EOF'
feat(api): streaming chat with cancel and tenant-safe task ownership

EOF
)"
```

---

### Task 14: Observe — audit + usage basics

**Files:**
- Schema: `gw_audit_event`, `gw_usage_event` (both with `tenant_id`)
- Create: `observe/.../JdbcAuditService.java`, `JdbcUsageService.java`
- Test: events always stored with tenant; query API cannot read other tenant

- [ ] **Step 1–3: TDD implement confirm/deny/cancel/usage write paths; commit**

```bash
git commit -m "$(cat <<'EOF'
feat(observe): tenant-scoped audit and usage events

EOF
)"
```

---

### Task 15: Isolation regression suite + docker compose

**Files:**
- Create: `bootstrap/src/test/java/.../TenantIsolationIT.java`
- Create: `docker-compose.yml` (Postgres + Redis)
- Create: `README.md` run instructions

- [ ] **Step 1: Write IT covering**
  1. Tenant A cannot read B conversation/state
  2. Tenant A cannot confirm B task
  3. Tenant A cannot list B MCP servers
  4. RunGate saturation on A does not block B
  5. API key for A never resolves as B

- [ ] **Step 2: Add compose + README quickstart**

```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: agent_gateway
      POSTGRES_USER: gateway
      POSTGRES_PASSWORD: gateway
    ports: ["5432:5432"]
  redis:
    image: redis:7
    ports: ["6379:6379"]
```

- [ ] **Step 3: Run full verify**

```bash
docker compose up -d
mvn verify
```

Expected: all modules green.

- [ ] **Step 4: Final commit**

```bash
git commit -m "$(cat <<'EOF'
test: add tenant isolation IT and local docker compose

EOF
)"
```

---

## Out of scope for this plan (do not implement now)

- Long-term memory / compaction
- Skills masking
- React admin console
- Per-tenant physical DB
- Billing ledger
- Anything inside the `ragent` repository except keeping the design/plan docs already committed there

---

## Spec coverage checklist

| Spec requirement | Task(s) |
|---|---|
| New greenfield repo | 1–2 |
| Control / data plane split | 6 vs 13 |
| Tenant + API key + quota | 4–6, 10 |
| Per-tenant MCP registry | 7–8 |
| ReAct SSE chat | 12–13 |
| Confirm gate | 11, 13 |
| Session state | 9 |
| RunGate | 10 |
| Trace/usage/audit basics | 14 |
| Logical SaaS isolation rules | 3, 5, 7–11, 15 |
| Failure: MCP unavailable | 8 |
| Failure: cancel + pending recovery | 12–13 |
| Failure: confirm timeout | 11 |
| No RAG | enforced by module set |

## Self-review notes

- No implementation steps target `ragent/` source trees.
- Types aligned on `TenantContext`, `tenantId + conversationId`, `ResolvedCatalog.fingerprint()`.
- Post-MVP items explicitly excluded to keep this plan shippable.
