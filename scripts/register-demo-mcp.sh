#!/usr/bin/env bash
# 将本地 demo mcp-server 登记到 gateway（需 ADMIN Key + 已有租户）
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_KEY="${GATEWAY_ADMIN_API_KEY:-sk_admin_local_dev}"
TENANT_ID="${TENANT_ID:-}"
MCP_URL="${MCP_URL:-http://127.0.0.1:19099/mcp}"

if [[ -z "$TENANT_ID" ]]; then
  echo "用法: TENANT_ID=<tenantId> $0"
  echo "可选: BASE_URL / GATEWAY_ADMIN_API_KEY / MCP_URL"
  exit 1
fi

echo "==> register demo MCP for tenant=$TENANT_ID url=$MCP_URL"
curl -sf -X POST "$BASE_URL/admin/tenants/$TENANT_ID/mcp-servers" \
  -H "Authorization: Bearer $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"demo-mcp\",\"name\":\"demo-mcp\",\"transportUrl\":\"$MCP_URL\",\"status\":\"ACTIVE\"}"
echo

echo "==> list MCP servers"
curl -sf "$BASE_URL/admin/tenants/$TENANT_ID/mcp-servers" \
  -H "Authorization: Bearer $ADMIN_KEY"
echo

echo "done. 只读工具默认免确认；demo_ticket_submit（写）会走 ConfirmGate"
echo "对话示例: 上海天气怎么样 / 直接调用 demo_ticket_submit，title=打印机坏了"
echo "批准: POST /v1/confirms/{confirmId}/approve"
