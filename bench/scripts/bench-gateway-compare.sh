#!/usr/bin/env bash
# Go vs Java vs 直连（由 bench-gateway-fair.sh 调用）
#   BENCH_USER=bench_user PASS=123456 ./bench/scripts/bench-gateway-compare.sh

set -euo pipefail

GO_BASE="${GO_BASE:-http://127.0.0.1:8080}"
JAVA_BASE="${JAVA_BASE:-http://127.0.0.1:8088}"
DIRECT_BASE="${DIRECT_BASE:-http://127.0.0.1:9001}"
USER_ID="${USER_ID:-1}"
THREADS="${THREADS:-4}"
CONNECTIONS="${CONNECTIONS:-50}"
DURATION="${DURATION:-20s}"
TOKEN="${TOKEN:-}"
PASS="${PASS:-123456}"
HMAC_SECRET="${INTERNAL_HMAC_SECRET:-}"

if [[ -n "${BENCH_USER:-}" ]]; then
  USER_NAME="${BENCH_USER}"
elif [[ -n "${USER:-}" && "${USER}" != "$(id -un 2>/dev/null || true)" ]]; then
  USER_NAME="${USER}"
else
  USER_NAME="bench_user"
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

if ! command -v wrk >/dev/null 2>&1; then
  echo "需要 wrk：sudo apt install wrk"
  exit 1
fi
if ! command -v openssl >/dev/null 2>&1; then
  echo "需要 openssl"
  exit 1
fi

if [[ -z "${HMAC_SECRET}" && -f "${REPO_ROOT}/.env" ]]; then
  HMAC_SECRET=$(grep -E '^INTERNAL_HMAC_SECRET=' "${REPO_ROOT}/.env" | head -n1 | cut -d= -f2- | tr -d '\r' | sed 's/^["'\'']//;s/["'\'']$//')
fi
if [[ -z "${HMAC_SECRET}" ]]; then
  echo "需要 INTERNAL_HMAC_SECRET"
  exit 1
fi

if [[ -z "${TOKEN}" ]]; then
  echo "登录 ${GO_BASE}/api/user/login ..."
  LOGIN_JSON=$(curl -sS -X POST "${GO_BASE}/api/user/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${USER_NAME}\",\"password\":\"${PASS}\",\"deviceFingerprint\":\"bench-gw-compare-fp\"}")
  TOKEN=$(echo "${LOGIN_JSON}" | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
  if [[ -z "${TOKEN}" ]]; then
    TOKEN=$(echo "${LOGIN_JSON}" | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
  fi
  if [[ -z "${TOKEN}" ]]; then
    echo "登录失败：${LOGIN_JSON}"
    exit 1
  fi
fi

sign_internal() {
  local uid="$1" ts="$2"
  printf '%s' "${uid}|${ts}" | openssl dgst -sha256 -hmac "${HMAC_SECRET}" | awk '{print $NF}'
}

run() {
  local title="$1"; shift
  echo
  echo "======== ${title} ========"
  echo "cmd: wrk -t${THREADS} -c${CONNECTIONS} -d${DURATION} $*"
  wrk -t"${THREADS}" -c"${CONNECTIONS}" -d"${DURATION}" "$@"
}

PATH_API="/api/user/${USER_ID}"
TS="$(date +%s)"
SIGN="$(sign_internal "${USER_ID}" "${TS}")"

echo "对比压测 USER_ID=${USER_ID} t=${THREADS} c=${CONNECTIONS} d=${DURATION}"
echo "冒烟："
curl -sS -o /dev/null -w "go %{http_code}\n" -H "Authorization: Bearer ${TOKEN}" "${GO_BASE}${PATH_API}" || true
curl -sS -o /dev/null -w "java %{http_code}\n" -H "Authorization: Bearer ${TOKEN}" "${JAVA_BASE}${PATH_API}" || true
curl -sS -o /dev/null -w "direct %{http_code}\n" \
  -H "X-User-Id: ${USER_ID}" -H "X-Gateway-Ts: ${TS}" -H "X-Gateway-Sign: ${SIGN}" \
  "${DIRECT_BASE}${PATH_API}" || true

run "A) 直连 user-service + HMAC" \
  -H "X-User-Id: ${USER_ID}" \
  -H "X-Gateway-Ts: ${TS}" \
  -H "X-Gateway-Sign: ${SIGN}" \
  "${DIRECT_BASE}${PATH_API}"

run "B) Go Gateway :8080 + JWT" \
  -H "Authorization: Bearer ${TOKEN}" \
  "${GO_BASE}${PATH_API}"

run "C) Spring Cloud Gateway :8088 + JWT" \
  -H "Authorization: Bearer ${TOKEN}" \
  "${JAVA_BASE}${PATH_API}"

echo
echo "对比 Requests/sec：直连 vs Go vs Java(SCG)。Go 请用 bench/config/gateway-wsl-compare.yaml（关中间件）。"
