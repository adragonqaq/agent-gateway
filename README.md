# Agent Gateway

Multi-tenant **ReAct Agent** service focused on **MCP tools** — not RAG.

Inspired by Ragent’s Agent module (middleware, confirm gate, run gate, SSE), but this is a **standalone** repository. No knowledge ingestion, hybrid retrieval, or intent-tree RAG.

## Modules

| Module | Role |
|---|---|
| `tenant` | Tenants, API keys, quotas, policy |
| `tools` | MCP registry + tool catalog |
| `memory` | Conversation / agent state |
| `runtime` | AgentScope ReAct, confirm, run gate |
| `observe` | Audit + usage |
| `gateway-api` | HTTP / SSE |
| `bootstrap` | Spring Boot entry |

## Docs

- Design: [`docs/specs/2026-11-05-react-agent-gateway-design.md`](docs/specs/2026-11-05-react-agent-gateway-design.md)
- Plan: [`docs/plans/2026-11-05-react-agent-gateway-mvp.md`](docs/plans/2026-11-05-react-agent-gateway-mvp.md)

## Status

Scaffolding MVP. See the implementation plan for task order.
