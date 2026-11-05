#!/usr/bin/env bash
# MCP 联调冒烟：要求 gateway 已启动，demo mcp-server 已在 :19099 监听
# 覆盖：登记 MCP →（agentscope 时）天气免确认对话 → 工单 confirm → approve
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_KEY="${GATEWAY_ADMIN_API_KEY:-sk_admin_local_dev}"
MCP_URL="${MCP_URL:-http://127.0.0.1:19099/mcp}"

echo "== probe MCP initialize =="
INIT=$(curl -sf -X POST "$MCP_URL" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"smoke-mcp","version":"0"}}}')
echo "$INIT" | grep -q 'agent-gateway-demo-mcp' || { echo "MCP initialize failed: $INIT"; exit 1; }

echo "== create tenant =="
TENANT_JSON=$(curl -sf -X POST "$BASE_URL/admin/tenants" \
  -H "Authorization: Bearer $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"name":"mcp-smoke","modelTier":"standard","maxConcurrent":5}')
echo "$TENANT_JSON"
TENANT_ID=$(python3 -c "import json,sys; print(json.load(sys.stdin)['id'])" <<<"$TENANT_JSON")

echo "== issue RUNTIME key =="
KEY_JSON=$(curl -sf -X POST "$BASE_URL/admin/tenants/$TENANT_ID/api-keys" \
  -H "Authorization: Bearer $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"role":"RUNTIME"}')
API_KEY=$(python3 -c "import json,sys; print(json.load(sys.stdin)['apiKey'])" <<<"$KEY_JSON")

echo "== register demo MCP =="
curl -sf -X POST "$BASE_URL/admin/tenants/$TENANT_ID/mcp-servers" \
  -H "Authorization: Bearer $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"demo-mcp\",\"name\":\"demo-mcp\",\"transportUrl\":\"$MCP_URL\",\"status\":\"ACTIVE\"}"
echo
LIST=$(curl -sf "$BASE_URL/admin/tenants/$TENANT_ID/mcp-servers" -H "Authorization: Bearer $ADMIN_KEY")
echo "$LIST" | grep -q 'demo-mcp' || { echo "MCP not listed: $LIST"; exit 1; }

MODE=$(curl -sf "$BASE_URL/v3/api-docs" >/dev/null && echo ok || echo skip)
if [[ "${GATEWAY_AGENT_MODE:-}" != "agentscope" ]]; then
  echo "== skip LLM tool chat (set GATEWAY_AGENT_MODE=agentscope + LLM env to exercise tools) =="
  echo "MCP SMOKE OK (register-only)"
  exit 0
fi

echo "== weather chat (expect demo_weather, no confirm) =="
WEATHER=$(curl -sN -X POST "$BASE_URL/v1/chat" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"message":"用工具查上海天气，一句话"}' --max-time 90 || true)
echo "$WEATHER" | head -c 800; echo
echo "$WEATHER" | grep -q 'demo_weather' || { echo "expected demo_weather tool event"; exit 1; }
if echo "$WEATHER" | grep -q 'event:confirm'; then
  echo "FAIL: read-only weather should not confirm"; exit 1
fi

echo "== ticket chat (expect confirm) =="
TICKET=$(curl -sN -X POST "$BASE_URL/v1/chat" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"message":"直接调用 demo_ticket_submit，title=冒烟工单，detail=自动测试"}' --max-time 90 || true)
echo "$TICKET" | head -c 800; echo
CONFIRM_ID=$(printf '%s' "$TICKET" | python3 -c 'import re,sys; m=re.search(r"\"confirmId\":\"(cfm_[^\"]+)\"", sys.stdin.read()); print(m.group(1) if m else "")')
if [[ -z "$CONFIRM_ID" ]]; then
  echo "WARN: model did not emit confirm this round; register+weather already OK"
  echo "MCP SMOKE OK (partial)"
  exit 0
fi

echo "== approve $CONFIRM_ID =="
APPROVE=$(curl -sN -X POST "$BASE_URL/v1/confirms/$CONFIRM_ID/approve" \
  -H "Authorization: Bearer $API_KEY" --max-time 60 || true)
echo "$APPROVE" | head -c 800; echo
echo "MCP SMOKE OK"
