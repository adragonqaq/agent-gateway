# Changelog

## 0.1.0 — 2026-11-05

### 功能
- 多租户 API Key 鉴权（RUNTIME / ADMIN）
- MCP 注册与租户工具目录；部分 Server 宕机时保留健康工具并标记 unavailable
- ReAct SSE 对话（echo / agentscope）；ConfirmGate approve/deny 续跑
- cancel → AgentScope interrupt；JDBC AgentStateStore 支撑 pending tool recovery
- RunGate（memory / redis）；审计与用量查询 API
- Springdoc OpenAPI（`/swagger-ui.html`）
- 确认单超时扫描写 `CONFIRM_EXPIRED`（续跑过期 → 410）

### 稳定性
- 修复 Boot 3.4：`TenantConfiguration` 去掉非法的 `@ComponentScan`+`@ConditionalOnBean`
- 默认排除 Redisson 自动配置（仅 `gateway.run-gate.type=redis` 时显式建连）
- Schema 初始化优先于 ADMIN 种子；提供 `application-h2` 无 Docker 冒烟

### 本地冒烟
```bash
# 无 Docker：H2 + echo
mvn -pl bootstrap -am spring-boot:run -Dspring-boot.run.profiles=h2
./scripts/smoke.sh

# 有 Docker：Postgres + Redis
docker compose up -d
mvn -pl bootstrap -am spring-boot:run
./scripts/smoke.sh
```

### 生产注意
- 关闭或保护 Swagger：`springdoc.api-docs.enabled=false`
- 必须设置强随机 `GATEWAY_SECRET_MASTER_KEY`；勿使用默认种子 ADMIN Key
- agentscope 模式默认 `gateway.agent.state-store=jdbc`
