#!/usr/bin/env bash
# 将本地 demo mcp-server 登记到 gateway（需 ADMIN Key + 已有租户）
# 若 MCP 启用了鉴权，设置 MCP_AUTH_TOKEN 后会把 Authorization Header 写入密文
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_KEY="${GATEWAY_ADMIN_API_KEY:-sk_admin_local_dev}"
TENANT_ID="${TENANT_ID:-}"
MCP_URL="${MCP_URL:-http://127.0.0.1:19099/mcp}"
MCP_AUTH_TOKEN="${MCP_AUTH_TOKEN:-}"

if [[ -z "$TENANT_ID" ]]; then
  echo "用法: TENANT_ID=<tenantId> $0"
  echo "可选: BASE_URL / GATEWAY_ADMIN_API_KEY / MCP_URL / MCP_AUTH_TOKEN"
  exit 1
fi

export MCP_URL MCP_AUTH_TOKEN
# 有 token 时一并登记 headers，走 SecretBox 加密落库
if [[ -n "$MCP_AUTH_TOKEN" ]]; then
  AUTH_STATE=on
  BODY=$(python3 -c 'import json,os; print(json.dumps({
    "id":"demo-mcp","name":"demo-mcp",
    "transportUrl":os.environ["MCP_URL"],
    "status":"ACTIVE",
    "headers":{"Authorization":"Bearer "+os.environ["MCP_AUTH_TOKEN"]}
  }))')
else
  AUTH_STATE=off
  BODY=$(python3 -c 'import json,os; print(json.dumps({
    "id":"demo-mcp","name":"demo-mcp",
    "transportUrl":os.environ["MCP_URL"],
    "status":"ACTIVE"
  }))')
fi

echo "==> register demo MCP for tenant=$TENANT_ID url=$MCP_URL auth=$AUTH_STATE"
curl -sf -X POST "$BASE_URL/admin/tenants/$TENANT_ID/mcp-servers" \
  -H "Authorization: Bearer $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d "$BODY"
echo

echo "==> list MCP servers"
curl -sf "$BASE_URL/admin/tenants/$TENANT_ID/mcp-servers" \
  -H "Authorization: Bearer $ADMIN_KEY"
echo

echo "done. 只读工具默认免确认；demo_ticket_submit（写）会走 ConfirmGate"
echo "对话示例: 上海天气怎么样 / 直接调用 demo_ticket_submit，title=打印机坏了"
echo "批准: POST /v1/confirms/{confirmId}/approve"
