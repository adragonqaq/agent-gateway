# Agent Gateway

Multi-tenant **ReAct Agent** service focused on **MCP tools** — not RAG.

Inspired by Ragent’s Agent module (middleware, confirm gate, run gate, SSE), but this is a **standalone** repository.

## Modules

| Module | Role |
|---|---|
| `tenant` | Tenants, API keys, quotas, policy |
| `tools` | MCP registry + tool catalog |
| `memory` | Conversation / agent state |
| `runtime` | Chat orchestration, confirm, run gate |
| `observe` | Audit + usage |
| `gateway-api` | HTTP / SSE |
| `bootstrap` | Spring Boot entry |

## Quick start

```bash
docker compose up -d   # Postgres + Redis (Redis optional for MVP; RunGate is in-memory)
export GATEWAY_SECRET_MASTER_KEY=0123456789abcdef
# point spring.datasource at local Postgres, then:
mvn -pl bootstrap -am spring-boot:run
```

MVP agent mode defaults to `gateway.agent.mode=echo` (deterministic stub). AgentScope wiring is next.

## API sketch

- `POST /admin/tenants` (ADMIN key)
- `POST /admin/tenants/{id}/api-keys` (ADMIN key) — returns raw key once
- `POST /v1/chat` (SSE) — Bearer / X-Api-Key
- `POST /v1/runs/{taskId}/cancel`
- `POST /v1/confirms/{id}/approve|deny`

## Docs

- Design: [`docs/specs/2026-11-05-react-agent-gateway-design.md`](docs/specs/2026-11-05-react-agent-gateway-design.md)
- Plan: [`docs/plans/2026-11-05-react-agent-gateway-mvp.md`](docs/plans/2026-11-05-react-agent-gateway-mvp.md)

## Verify

```bash
mvn test
```

## Agent mode

- `gateway.agent.mode=echo` (default): deterministic stub for local tests
- `gateway.agent.mode=agentscope`: real AgentScope ReAct + OpenAI-compatible model

```bash
export GATEWAY_AGENT_MODE=agentscope
export GATEWAY_LLM_API_KEY=sk-...
export GATEWAY_LLM_BASE_URL=https://api.openai.com
export GATEWAY_LLM_MODEL=gpt-4o-mini
```

Catalog tools are currently registered as stub AgentTools until MCP proxy execution is wired.
