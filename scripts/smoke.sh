#!/usr/bin/env bash
# Agent Gateway 最小联调脚本（默认打本机 8080，echo 模式）
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_KEY="${GATEWAY_ADMIN_API_KEY:-sk_admin_local_dev}"

echo "== health =="
curl -sf "$BASE_URL/actuator/health" 2>/dev/null || curl -sf "$BASE_URL/health" || true

echo "== create tenant =="
TENANT_JSON=$(curl -sf -X POST "$BASE_URL/admin/tenants" \
  -H "Authorization: Bearer $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"name":"smoke","modelTier":"standard","maxConcurrent":5}')
echo "$TENANT_JSON"
TENANT_ID=$(python3 -c "import json,sys; print(json.load(sys.stdin)['id'])" <<<"$TENANT_JSON")

echo "== issue key =="
KEY_JSON=$(curl -sf -X POST "$BASE_URL/admin/tenants/$TENANT_ID/api-keys" \
  -H "Authorization: Bearer $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"role":"RUNTIME"}')
echo "$KEY_JSON"
API_KEY=$(python3 -c "import json,sys; print(json.load(sys.stdin)['apiKey'])" <<<"$KEY_JSON")

echo "== chat SSE (5s) =="
curl -sN -X POST "$BASE_URL/v1/chat" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"message":"hello smoke"}' --max-time 5 || true

echo
echo "== observe =="
curl -sf "$BASE_URL/v1/observe/usage" -H "Authorization: Bearer $API_KEY"
echo
curl -sf "$BASE_URL/v1/observe/audit?limit=5" -H "Authorization: Bearer $API_KEY"
echo
echo "SMOKE OK"
