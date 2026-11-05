# Multi-tenant ReAct Agent Gateway — Design Spec

**Date:** 2026-11-05  
**Status:** Draft for review  
**Working name:** Agent Gateway（正式产品名待定）  
**Stack:** Java 17+ · Spring Boot · AgentScope ReAct · MCP · Postgres · Redis

## 1. Goal

Build a **greenfield, production-ready, multi-tenant ReAct Agent service** focused on **general MCP tool use**, not RAG.

Inspired by Ragent’s Agent module (middleware chain, tool catalog, confirm gate, run gate, state recovery, SSE/trace), but **a new repository** that does not carry knowledge ingestion, hybrid retrieval, or intent-tree RAG orchestration.

### Confirmed decisions

| Decision | Choice |
|---|---|
| Purpose | Production usable |
| Workload | General MCP tool agent (task types not fixed) |
| Delivery | Deployable Agent service (HTTP/SSE, multi-session) |
| Stack | Java + AgentScope |
| Starting point | New repo; borrow Ragent Agent design seams only |
| Architecture style | Multi-tenant Agent Gateway |
| Tenant model | External SaaS with strong logical isolation |

### Non-goals (v1)

- RAG / document ingestion / knowledge-base intent trees
- Per-tenant physical database or VPC isolation
- Full billing ledger / payment integration
- Long-term memory + context compaction (post-MVP)
- Skills progressive disclosure (post-MVP)
- Full admin console UI polish (MVP may ship API-only or minimal admin)

## 2. Architecture

Control plane and data plane are separated.

```
┌─────────────────────────────────────────────┐
│  Control Plane                                │
│  Tenants / API Keys / MCP registry /          │
│  Policy / Quotas / Audit                        │
└──────────────────┬──────────────────────────┘
                   │ config snapshots
┌──────────────────▼──────────────────────────┐
│  Data Plane                                   │
│  HTTP/SSE Chat · RunGate · ReAct + middleware │
│  Tenant tool catalog · ConfirmGate · State    │
└──────────────────┬──────────────────────────┘
                   │ tenant-scoped connections
┌──────────────────▼──────────────────────────┐
│  Tenant Isolation                             │
│  tenant_id everywhere · secrets isolated      │
│  MCP pools per tenant · quotas per tenant     │
└─────────────────────────────────────────────┘
```

### Modules (Maven)

| Module | Responsibility |
|---|---|
| `tenant` | Tenant, API key, quota, policy models & services |
| `tools` | MCP registry, tool catalog, proxies, confirm metadata |
| `runtime` | ReActAgent provider, middleware chain, run lifecycle |
| `memory` | Session/agent state store (MVP); later long-term memory |
| `observe` | Trace enrichment, usage metering, audit hooks |
| `gateway-api` | HTTP/SSE controllers, DTOs, auth filters |
| `bootstrap` | Spring Boot entry + main config |

Knowledge search is **not** a first-class reserved tool. Tenants expose retrieval (if any) via their own MCP servers.

## 3. Request flow

```
API Key
  → TenantResolver (tenant, role, model tier)
  → Quota / RunGate
  → SessionStore.load(tenantId, conversationId)
  → ToolCatalog.resolve(tenant MCP + PolicyEngine filter)
  → ReActAgent (+ middlewares)
        · confirm-denial rewrite
        · tool-batch + tracing
        · (later) user-memory / compaction / skill-masking
  → SSE: thinking / tool / confirm / answer
  → StateSave + Audit + Usage
```

### Core components

- **TenantResolver** — API key → tenant context (id, role, model tier, credential binding)
- **McpRegistry** — per-tenant MCP server configs; fingerprint for hot rebuild of toolkit
- **PolicyEngine** — tool allow/deny lists; which tools require human confirm
- **RunGate** — per-tenant concurrency + fair queue (pattern from Ragent Redis gate)
- **ReActRuntime** — AgentScope `ReActAgent` supplier; middleware-pluggable
- **ConfirmGate** — pause write tools → confirm card → continue / deny
- **SessionState** — persist agent state keyed by `tenantId + conversationId`

## 4. Tenant isolation & security

### Isolation matrix (v1)

| Asset | Isolation |
|---|---|
| API keys | Per tenant |
| Model credential refs | Per tenant (or platform-billed quota binding; never cross-tenant) |
| MCP connections & secrets | Per tenant |
| Conversations / agent state | Per tenant |
| Traces / audit / usage | Per tenant |
| JVM / Postgres / Redis | Shared instance, logical isolation via `tenant_id` |

**Deferred:** dedicated DB / VPC per tenant (keep as upgrade path; data model must not block it).

### Hard rules

1. Every read/write path carries `tenant_id`; repositories reject queries without tenant predicate.
2. MCP credentials live in a secret store or encrypted column; never plaintext in logs or traces.
3. Cross-tenant `taskId` / `conversationId` access returns **not found** (no existence leak).
4. Write tools **require confirm by default**; tenant policy may tighten, not globally loosen the default.
5. Model calls use only the tenant’s bound provider credentials or the platform quota binding for that tenant — never another tenant’s pool.

## 5. Failure handling

| Failure | Behavior |
|---|---|
| MCP unreachable | Mark tools unavailable; do not abort the whole run; expose degraded tool list to the model |
| Single tool timeout/error | Write error result into the loop; ReAct may continue; **no auto-retry for write tools** |
| Client cancel / force stop | Interrupt stream + graceful state-save window; pending tool calls recover with error results on next turn (Ragent-aligned) |
| Quota exhausted | Queue or HTTP 429; never borrow another tenant’s quota |
| Confirm timeout | Expire suspended run as denied; write audit event |
| Model failure | Fail over only within the tenant’s configured tier candidates |

## 6. MVP scope

### In MVP

- Tenant + API key + basic quota
- Per-tenant MCP registration and tool catalog
- ReAct chat over SSE (thinking / tool / confirm / answer)
- Confirm gate for write tools + session state persistence
- RunGate concurrency control
- Basic trace + usage metering + audit of confirm/deny/cancel

### Post-MVP

- Long-term memory + context compaction
- Skills (progressive disclosure / masking)
- Full admin console UI
- Physical isolation upgrade path
- Billing reconciliation

## 7. Testing priorities

1. Cross-tenant isolation regression (session, task, MCP, secrets)
2. Confirm gate: confirm / deny / timeout
3. Cancel + pending-tool recovery
4. Partial MCP failure (catalog + execution)
5. RunGate quota and fair queue behavior

Regression suites should be tenant-aware from day one (fixture tenants A/B, assert no cross-leak).

## 8. Relation to Ragent

**Borrow (ideas / patterns, reimplemented cleanly):**

- ReAct + middleware composition
- Tool catalog fingerprint + reserved tool names
- Confirm gate UX contract (SSE confirm events)
- Run gate / stream task ownership
- Pending tool recovery after forced stop
- Trace node enrichment around reasoning / tool batches

**Do not copy:**

- RAG pipeline, hybrid retrieval, ingestion
- Intent-tree knowledge routing as the primary orchestrator
- KnowledgeSearch as a platform-owned reserved tool (unless later added as an optional plugin)

## 9. Open items (explicit, non-blocking)

- Formal product name and repo name
- AuthN detail: API key only vs API key + OIDC for control plane operators
- Whether MVP ships a minimal React admin or API + OpenAPI only
- Secret backend: env/KMS/Vault choice for first deploy target

These do not change the architecture above; resolve at implementation-plan time.
