#!/usr/bin/env bash

set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8080}
EMAIL=${SMOKE_EMAIL:-}
PASSWORD=${SMOKE_PASSWORD:-}

log() {
  printf '[smoke] %s\n' "$*"
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command '$1' not found" >&2
    exit 1
  fi
}

require_cmd curl
require_cmd python3

if [ -z "$EMAIL" ] || [ -z "$PASSWORD" ]; then
  cat <<'EOF' >&2
[smoke] Missing SMOKE_EMAIL/SMOKE_PASSWORD environment variables.
[smoke] Provide credentials for an already activated account, e.g.:
[smoke]   SMOKE_EMAIL=user@example.com SMOKE_PASSWORD=secret scripts/smoke.sh
EOF
  exit 1
fi

log "Base URL: $BASE_URL"
log "Using account: $EMAIL"

login_response=$(curl -sS --fail -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")

TOKEN=$(printf '%s' "$login_response" | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
log "Obtained token"

AUTH_HEADER="Authorization: Bearer $TOKEN"

create_payload='{ "title": "Smoke Todo", "description": "Created by smoke test" }'
create_response=$(curl -sS --fail -X POST "$BASE_URL/api/todos" \
  -H 'Content-Type: application/json' -H "$AUTH_HEADER" \
  -d "$create_payload")

TODO_ID=$(printf '%s' "$create_response" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
log "Created todo $TODO_ID"

list_response=$(curl -sS --fail -X GET "$BASE_URL/api/todos" -H "$AUTH_HEADER")
log "List response: $list_response"

toggle_response=$(curl -sS --fail -X PATCH "$BASE_URL/api/todos/$TODO_ID/toggle" -H "$AUTH_HEADER")
log "Toggled todo"

delete_response=$(curl -sS --fail -X DELETE "$BASE_URL/api/todos/$TODO_ID" -H "$AUTH_HEADER")
log "Deleted todo"

log "Smoke test completed successfully"
