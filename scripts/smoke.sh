#!/usr/bin/env bash

set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8080}
EMAIL="smoke_${RANDOM}@example.com"
PASSWORD="P@ssw0rd123"

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

log "Base URL: $BASE_URL"

signup_response=$(curl -sS --fail -X POST "$BASE_URL/api/auth/signup" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")

log "Signup response: $signup_response"

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
