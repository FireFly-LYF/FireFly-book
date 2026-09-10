#!/usr/bin/env bash
# 业务读公平对比（每接口预热 + 交替先后）
#   THREADS=4 CONNECTIONS=50 DURATION=20s ./bench/scripts/bench-business-fair.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BENCH_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${BENCH_ROOT}/.." && pwd)"
REPORT_DIR="${BENCH_ROOT}/reports"
GW_DIR="${REPO_ROOT}/Gateway/gateway"
mkdir -p "${REPORT_DIR}"

TS="$(date +%Y%m%d-%H%M%S)"
REPORT="${REPORT_DIR}/biz-fair-${TS}.md"
RAW="${REPORT_DIR}/biz-fair-${TS}.raw.txt"
GO_LOG="/tmp/firefly-go-gateway-fair.log"
JAVA_LOG="/tmp/firefly-java-gateway-fair.log"

THREADS="${THREADS:-4}"
CONNECTIONS="${CONNECTIONS:-50}"
DURATION="${DURATION:-20s}"
GO_BASE="${GO_BASE:-http://127.0.0.1:8080}"
JAVA_BASE="${JAVA_BASE:-http://127.0.0.1:8088}"
SKIP_SETUP="${SKIP_SETUP:-0}"
FORCE_RESTART_GW="${FORCE_RESTART_GW:-0}"

export PATH="/usr/local/go/bin:${HOME}/go/bin:/usr/bin:/bin:${PATH}"

cd "${REPO_ROOT}"
set -a
# shellcheck disable=SC1091
source "${REPO_ROOT}/.env"
set +a

chmod +x "${SCRIPT_DIR}/"*.sh 2>/dev/null || true

kill_port() {
  local port="$1" pids
  pids=$(ss -ltnp 2>/dev/null | awk -v p=":${port}" '$4 ~ p {print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | sort -u)
  if [[ -n "${pids}" ]]; then
    # shellcheck disable=SC2086
    kill ${pids} 2>/dev/null || true
    sleep 1
  fi
}

wait_health() {
  local url="$1" name="$2"
  for _ in $(seq 1 60); do
    if curl -fsS -m 2 "${url}" >/dev/null 2>&1; then
      echo "${name} ok"
      return 0
    fi
    sleep 2
  done
  echo "${name} 未就绪：${url}"
  return 1
}

if [[ "${SKIP_SETUP}" != "1" ]]; then
  echo "== 停 Docker Gateway =="
  docker stop firefly-gateway-1 >/dev/null 2>&1 || true

  if ! curl -sS -m 2 -o /dev/null -w "%{http_code}" "http://127.0.0.1:9001/api/user/1" | grep -qE '^[0-9]+$'; then
    echo "请先：docker compose -f deploy/docker-compose.yml -f bench/compose/docker-compose.bench.yml --env-file .env up -d"
    exit 1
  fi

  if ! command -v go >/dev/null 2>&1; then
    echo "需要 Go"
    exit 1
  fi

  if ! curl -fsS -m 2 "${GO_BASE}/gateway/health" >/dev/null 2>&1 || [[ "${FORCE_RESTART_GW}" == "1" ]]; then
    echo "== 启动 Go Gateway =="
    kill_port 8080
    (
      cd "${GW_DIR}"
      nohup go run ./cmd/gateway -config "${BENCH_ROOT}/config/gateway-wsl-compare.yaml" >"${GO_LOG}" 2>&1 &
      echo $! >/tmp/firefly-go-gateway-fair.pid
    )
  else
    echo "Go Gateway 已在监听"
  fi

  if ! curl -fsS -m 2 "${JAVA_BASE}/gateway/health" >/dev/null 2>&1 || [[ "${FORCE_RESTART_GW}" == "1" ]]; then
    echo "== 启动 Java Gateway =="
    kill_port 8088
    JAR="${BENCH_ROOT}/java-gateway/target/java-gateway-0.0.1-SNAPSHOT.jar"
    if [[ ! -f "${JAR}" ]]; then
      (cd "${BENCH_ROOT}/java-gateway" && mvn -q -DskipTests package)
    fi
    export SERVER_PORT=8088 SERVER_ADDRESS=0.0.0.0
    export USER_BASE_URL=http://127.0.0.1:9001
    export CONTENT_BASE_URL=http://127.0.0.1:9002
    export SOCIAL_BASE_URL=http://127.0.0.1:9004
    export NOTIFY_BASE_URL=http://127.0.0.1:9005
    nohup java -jar "${JAR}" >"${JAVA_LOG}" 2>&1 &
    echo $! >/tmp/firefly-java-gateway-fair.pid
  else
    echo "Java Gateway 已在监听"
  fi
fi

wait_health "${GO_BASE}/gateway/health" "Go"
wait_health "${JAVA_BASE}/gateway/health" "Java"

eval "$("${SCRIPT_DIR}/seed-bench.sh")"
: "${USER_ID:?}"
: "${NOTE_ID:?}"
: "${TOKEN:?}"

NPROC="$(nproc 2>/dev/null || echo '?')"
MEM="$(free -h 2>/dev/null | awk '/Mem:/{print $2}' || echo '?')"

echo "== 业务读对比 =="
BENCH_USER="${BENCH_USER:-bench_user}" PASS="${PASS:-123456}" \
  TOKEN="${TOKEN}" USER_ID="${USER_ID}" NOTE_ID="${NOTE_ID}" \
  GO_BASE="${GO_BASE}" JAVA_BASE="${JAVA_BASE}" \
  THREADS="${THREADS}" CONNECTIONS="${CONNECTIONS}" DURATION="${DURATION}" \
  "${SCRIPT_DIR}/bench-business-compare.sh" | tee "${RAW}"

extract_qps() {
  local tag="$1" title="$2"
  awk -v tag="$tag" -v title="$title" '
    index($0, "[" tag "]") && index($0, title) { hit=1; next }
    hit && /Requests\/sec:/ {
      for (i=1;i<=NF;i++) if ($i+0==$i) { print $i; exit }
    }
  ' "${RAW}" 2>/dev/null || echo "n/a"
}

cat > "${REPORT}" <<EOF
# 业务读公平对比 ${TS}

| 项 | 值 |
|----|-----|
| CPU / 内存 | ${NPROC} / ${MEM} |
| wrk | -t${THREADS} -c${CONNECTIONS} -d${DURATION} |
| USER_ID / NOTE_ID | ${USER_ID} / ${NOTE_ID} |

| 接口 | Go | Java |
|------|-----|------|
| user | $(extract_qps "Go" "读用户资料") | $(extract_qps "Java" "读用户资料") |
| note | $(extract_qps "Go" "读笔记详情") | $(extract_qps "Java" "读笔记详情") |
| like | $(extract_qps "Go" "赞数") | $(extract_qps "Java" "赞数") |
| comment | $(extract_qps "Go" "评论列表") | $(extract_qps "Java" "评论列表") |
| notify | $(extract_qps "Go" "通知列表") | $(extract_qps "Java" "通知列表") |

原始：\`bench/reports/biz-fair-${TS}.raw.txt\`
EOF

echo "报告：${REPORT}"
