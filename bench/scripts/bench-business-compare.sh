#!/usr/bin/env bash
# 业务读：同一接口立刻对比 Go / Java（交替先后）
#   TOKEN=... USER_ID=15 NOTE_ID=98 ./bench/scripts/bench-business-compare.sh

set -euo pipefail

GO_BASE="${GO_BASE:-http://127.0.0.1:8080}"
JAVA_BASE="${JAVA_BASE:-http://127.0.0.1:8088}"
USER_ID="${USER_ID:-1}"
NOTE_ID="${NOTE_ID:-1}"
THREADS="${THREADS:-4}"
CONNECTIONS="${CONNECTIONS:-50}"
DURATION="${DURATION:-20s}"
WARMUP_SEC="${WARMUP_SEC:-3s}"
TOKEN="${TOKEN:-}"
PASS="${PASS:-123456}"

if [[ -n "${BENCH_USER:-}" ]]; then
  USER_NAME="${BENCH_USER}"
elif [[ -n "${USER:-}" && "${USER}" != "$(id -un 2>/dev/null || true)" ]]; then
  USER_NAME="${USER}"
else
  USER_NAME="bench_user"
fi

if ! command -v wrk >/dev/null 2>&1; then
  echo "需要 wrk"
  exit 1
fi

if [[ -z "${TOKEN}" ]]; then
  echo "登录 ${GO_BASE}/api/user/login ..."
  LOGIN_JSON=$(curl -sS -X POST "${GO_BASE}/api/user/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${USER_NAME}\",\"password\":\"${PASS}\",\"deviceFingerprint\":\"bench-biz-compare-fp\"}")
  TOKEN=$(echo "${LOGIN_JSON}" | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
  if [[ -z "${TOKEN}" ]]; then
    TOKEN=$(echo "${LOGIN_JSON}" | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
  fi
  if [[ -z "${TOKEN}" ]]; then
    echo "登录失败：${LOGIN_JSON}"
    exit 1
  fi
fi

run() {
  local title="$1"; shift
  echo
  echo "======== ${title} ========"
  echo "cmd: wrk -t${THREADS} -c${CONNECTIONS} -d${DURATION} $*"
  wrk -t"${THREADS}" -c"${CONNECTIONS}" -d"${DURATION}" "$@"
}

warmup() {
  local tag="$1" base="$2" path="$3"
  echo "预热 [${tag}] ${path} (${WARMUP_SEC}) ..."
  wrk -t2 -c10 -d"${WARMUP_SEC}" -H "Authorization: Bearer ${TOKEN}" "${base}${path}" >/dev/null 2>&1 || true
}

smoke() {
  local base="$1" name="$2"
  echo "冒烟 ${name} ${base}："
  curl -sS -o /dev/null -w "  user %{http_code}\n" -H "Authorization: Bearer ${TOKEN}" "${base}/api/user/${USER_ID}" || true
  curl -sS -o /dev/null -w "  note %{http_code}\n" -H "Authorization: Bearer ${TOKEN}" "${base}/api/note/${NOTE_ID}" || true
  curl -sS -o /dev/null -w "  likeCount %{http_code}\n" -H "Authorization: Bearer ${TOKEN}" "${base}/api/social/like/${NOTE_ID}/count" || true
  curl -sS -o /dev/null -w "  comments %{http_code}\n" -H "Authorization: Bearer ${TOKEN}" "${base}/api/social/comment/${NOTE_ID}?page=1&size=20" || true
  curl -sS -o /dev/null -w "  notify %{http_code}\n" -H "Authorization: Bearer ${TOKEN}" "${base}/api/notify/list?page=1&size=20" || true
}

# 对单个接口：两侧都预热后，按 first 指定先后正式压测
pair() {
  local title="$1" path="$2" first="$3"
  echo
  echo "######## 接口：${title}（先后：${first} 先）########"
  warmup "Go" "${GO_BASE}" "${path}"
  warmup "Java" "${JAVA_BASE}" "${path}"
  if [[ "${first}" == "Go" ]]; then
    run "[Go] ${title}" -H "Authorization: Bearer ${TOKEN}" "${GO_BASE}${path}"
    run "[Java] ${title}" -H "Authorization: Bearer ${TOKEN}" "${JAVA_BASE}${path}"
  else
    run "[Java] ${title}" -H "Authorization: Bearer ${TOKEN}" "${JAVA_BASE}${path}"
    run "[Go] ${title}" -H "Authorization: Bearer ${TOKEN}" "${GO_BASE}${path}"
  fi
}

echo "业务对比 USER_ID=${USER_ID} NOTE_ID=${NOTE_ID} t=${THREADS} c=${CONNECTIONS} d=${DURATION}"
echo "策略：每接口两侧预热 ${WARMUP_SEC}；相邻接口交替「谁先压」，避免整边预热偏差"
echo "写压测默认关闭"

smoke "${GO_BASE}" "Go"
smoke "${JAVA_BASE}" "Java"

# 奇数接口 Go 先，偶数接口 Java 先
pair "读用户资料 GET /api/user/{id}" "/api/user/${USER_ID}" "Go"
pair "读笔记详情 GET /api/note/{id}" "/api/note/${NOTE_ID}" "Java"
pair "赞数 GET /api/social/like/{id}/count" "/api/social/like/${NOTE_ID}/count" "Go"
pair "评论列表 GET /api/social/comment/{id}" "/api/social/comment/${NOTE_ID}?page=1&size=20" "Java"
pair "通知列表 GET /api/notify/list" "/api/notify/list?page=1&size=20" "Go"

echo
echo "对比同名接口在 [Go] / [Java] 标题下的 Requests/sec（已按接口交替先后）。"
