#!/usr/bin/env bash
# 压测造数：注册/登录 + 发一条无封面笔记（无 Python）
# 用法：
#   eval "$(./bench/scripts/seed-bench.sh)"

set -euo pipefail

BASE="${BASE:-http://127.0.0.1:8080}"
PASS="${PASS:-123456}"
NICK="${NICK:-Bench User}"
DEVICE="${DEVICE:-bench-wsl-device-fingerprint-01}"

# 勿把系统登录名（环境变量 USER）当成压测账号
if [[ -n "${BENCH_USER:-}" ]]; then
  USER_NAME="${BENCH_USER}"
elif [[ -n "${USER:-}" && "${USER}" != "$(id -un 2>/dev/null || true)" ]]; then
  USER_NAME="${USER}"
else
  USER_NAME="bench_user"
fi

json_get() {
  # json_get <json> <sed_pattern> — 无 jq 时用 sed；有 jq 则优先
  local raw="$1"
  local jq_path="$2"
  local sed_pat="$3"
  if command -v jq >/dev/null 2>&1; then
    echo "$raw" | jq -r "$jq_path // empty" 2>/dev/null || true
  else
    echo "$raw" | sed -n "s/.*${sed_pat}.*/\\1/p" | head -n1
  fi
}

echo "seed-bench: Gateway=${BASE} user=${USER_NAME}" >&2

LOGIN_BODY=$(printf '{"username":"%s","password":"%s","deviceFingerprint":"%s"}' \
  "$USER_NAME" "$PASS" "$DEVICE")
REG_BODY=$(printf '{"username":"%s","password":"%s","nickname":"%s","deviceFingerprint":"%s"}' \
  "$USER_NAME" "$PASS" "$NICK" "$DEVICE")

auth_parse() {
  local raw="$1"
  TOKEN=$(json_get "$raw" '.data.accessToken // .data.token' \
    '"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)"')
  if [[ -z "${TOKEN}" ]]; then
    TOKEN=$(json_get "$raw" '.data.token' '"token"[[:space:]]*:[[:space:]]*"\([^"]*\)"')
  fi
  if command -v jq >/dev/null 2>&1; then
    USER_ID=$(echo "$raw" | jq -r '.data.user.id // empty' 2>/dev/null || true)
  else
    USER_ID=$(echo "$raw" | sed -n 's/.*"user"[^{]*{[^}]*"id"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -n1)
  fi
}

LOGIN_JSON=$(curl -sS -X POST "${BASE}/api/user/login" \
  -H "Content-Type: application/json" \
  -d "${LOGIN_BODY}" || true)
auth_parse "${LOGIN_JSON}"

if [[ -z "${TOKEN:-}" ]]; then
  echo "login miss, try register..." >&2
  REG_JSON=$(curl -sS -X POST "${BASE}/api/user/register" \
    -H "Content-Type: application/json" \
    -d "${REG_BODY}")
  auth_parse "${REG_JSON}"
  if [[ -z "${TOKEN:-}" ]]; then
    LOGIN_JSON=$(curl -sS -X POST "${BASE}/api/user/login" \
      -H "Content-Type: application/json" \
      -d "${LOGIN_BODY}")
    auth_parse "${LOGIN_JSON}"
  fi
fi

if [[ -z "${TOKEN:-}" ]]; then
  echo "seed-bench failed: cannot get token. last login=${LOGIN_JSON:-}" >&2
  exit 1
fi

NOTE_JSON=$(curl -sS -X POST "${BASE}/api/note" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: seed-bench-note-1" \
  -d '{"title":"bench note","content":"seed for wrk performance test","coverUrl":null,"mediaUrls":[]}')

NOTE_ID=$(json_get "${NOTE_JSON}" '.data.note.id' \
  '"id"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\)')

if [[ -z "${NOTE_ID}" || "${NOTE_ID}" == "null" ]]; then
  echo "seed-bench: create note failed: ${NOTE_JSON}" >&2
  exit 1
fi

if [[ -z "${USER_ID:-}" || "${USER_ID}" == "null" ]]; then
  USER_ID=1
fi

echo "seed-bench ok: USER_ID=${USER_ID} NOTE_ID=${NOTE_ID}" >&2

# stdout 仅 export，便于 eval
cat <<EOF
export BENCH_USER='${USER_NAME}'
export USER='${USER_NAME}'
export PASS='${PASS}'
export USER_ID='${USER_ID}'
export NOTE_ID='${NOTE_ID}'
export TOKEN='${TOKEN}'
export BASE='${BASE}'
EOF
