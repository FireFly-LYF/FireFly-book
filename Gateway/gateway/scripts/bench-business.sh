#!/usr/bin/env bash
# FireFly 业务压测（WSL / Linux）
# 依赖：wrk、curl
# 用法：
#   chmod +x Gateway/gateway/scripts/bench-business.sh
#   # 经网关（需先登录拿 TOKEN，或脚本自动 login）
#   USER=alice PASS=123456 ./bench-business.sh
#   TOKEN=eyJ... NOTE_ID=10 ./bench-business.sh
#   # 直连某服务（不强制 JWT）
#   BASE=http://127.0.0.1:9002 NOTE_ID=10 ./bench-business.sh

set -euo pipefail

BASE="${BASE:-http://127.0.0.1:8080}"
USER_ID="${USER_ID:-1}"
NOTE_ID="${NOTE_ID:-1}"
THREADS="${THREADS:-4}"
CONNECTIONS="${CONNECTIONS:-50}"
DURATION="${DURATION:-20s}"
TOKEN="${TOKEN:-}"
USER_NAME="${USER:-}"
PASS="${PASS:-123456}"

if ! command -v wrk >/dev/null 2>&1; then
  echo "需要 wrk：sudo apt install wrk 或自行编译安装"
  exit 1
fi

# 经 Gateway 时自动登录拿 Bearer（白名单 /api/user/login）
if [[ -z "${TOKEN}" && "${BASE}" == *":8080"* ]]; then
  if [[ -z "${USER_NAME}" ]]; then
    echo "经网关压测需要 TOKEN，或设置 USER/PASS 自动登录，例如："
    echo "  USER=alice PASS=123456 $0"
    exit 1
  fi
  echo "登录 ${BASE}/api/user/login 用户=${USER_NAME} ..."
  LOGIN_JSON=$(curl -sS -X POST "${BASE}/api/user/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${USER_NAME}\",\"password\":\"${PASS}\"}")
  TOKEN=$(echo "${LOGIN_JSON}" | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
  if [[ -z "${TOKEN}" ]]; then
    echo "登录失败：${LOGIN_JSON}"
    exit 1
  fi
  echo "已拿到 JWT"
fi

HDR=()
if [[ -n "${TOKEN}" ]]; then
  HDR=(-H "Authorization: Bearer ${TOKEN}")
fi

run() {
  local title="$1"; shift
  echo
  echo "======== ${title} ========"
  echo "cmd: wrk -t${THREADS} -c${CONNECTIONS} -d${DURATION} $*"
  wrk -t"${THREADS}" -c"${CONNECTIONS}" -d"${DURATION}" "$@"
}

echo "业务压测 BASE=${BASE} USER_ID=${USER_ID} NOTE_ID=${NOTE_ID} auth=${TOKEN:+jwt}"
echo "先用 curl 冒烟："
curl -sS -o /dev/null -w "user %{http_code}\n" "${HDR[@]}" "${BASE}/api/user/${USER_ID}" || true
curl -sS -o /dev/null -w "note %{http_code}\n" "${HDR[@]}" "${BASE}/api/note/${NOTE_ID}" || true
curl -sS -o /dev/null -w "likeCount %{http_code}\n" "${HDR[@]}" "${BASE}/api/social/like/${NOTE_ID}/count" || true
curl -sS -o /dev/null -w "comments %{http_code}\n" "${HDR[@]}" "${BASE}/api/social/comment/${NOTE_ID}" || true
curl -sS -o /dev/null -w "notify %{http_code}\n" "${HDR[@]}" "${BASE}/api/notify/list?page=1&size=20" || true

run "读用户资料 GET /api/user/{id}" \
  "${HDR[@]}" "${BASE}/api/user/${USER_ID}"

run "读笔记详情 GET /api/note/{id}" \
  "${HDR[@]}" "${BASE}/api/note/${NOTE_ID}"

run "赞数 GET /api/social/like/{id}/count" \
  "${HDR[@]}" "${BASE}/api/social/like/${NOTE_ID}/count"

run "评论列表 GET /api/social/comment/{id}" \
  "${HDR[@]}" "${BASE}/api/social/comment/${NOTE_ID}"

run "通知列表 GET /api/notify/list" \
  "${HDR[@]}" "${BASE}/api/notify/list?page=1&size=20"

if [[ "${RUN_WRITE:-0}" == "1" ]]; then
  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  echo
  echo "写压测：并发评论。CONNECTIONS 建议 <=20"
  run "发评论 POST /api/social/comment" \
    -s "${SCRIPT_DIR}/wrk-comment.lua" \
    "${HDR[@]}" -H "Content-Type: application/json" \
    "${BASE}/api/social/comment"
else
  echo
  echo "写压测已跳过。开启：RUN_WRITE=1 CONNECTIONS=10 $0"
fi

echo
echo "看 Requests/sec、Latency、Non-2xx。"
