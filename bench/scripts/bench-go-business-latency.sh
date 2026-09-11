#!/usr/bin/env bash
# Go 业务读：中间件开（Redis 限流），wrk --latency 采集 Avg QPS / P50 / P99（无 Java 对比）
#   THREADS=4 CONNECTIONS=50 DURATION=20s ./bench/scripts/bench-go-business-latency.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BENCH_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${BENCH_ROOT}/.." && pwd)"
REPORT_DIR="${BENCH_ROOT}/reports"
GW_DIR="${REPO_ROOT}/Gateway/gateway"
mkdir -p "${REPORT_DIR}"

TS="$(date +%Y%m%d-%H%M%S)"
REPORT="${REPORT_DIR}/biz-go-latency-${TS}.md"
RAW="${REPORT_DIR}/biz-go-latency-${TS}.raw.txt"
GO_LOG="/tmp/firefly-go-gateway-middleware.log"

THREADS="${THREADS:-4}"
CONNECTIONS="${CONNECTIONS:-50}"
DURATION="${DURATION:-20s}"
WARMUP_SEC="${WARMUP_SEC:-3s}"
GO_BASE="${GO_BASE:-http://127.0.0.1:8080}"
SKIP_SETUP="${SKIP_SETUP:-0}"
FORCE_RESTART_GW="${FORCE_RESTART_GW:-1}"
GW_CONFIG="${GW_CONFIG:-${BENCH_ROOT}/config/gateway-wsl-middleware.yaml}"
# 接口之间冷却，减轻串测把整机打进过载（尤其 d=60s）
COOLDOWN_SEC="${COOLDOWN_SEC:-10}"

export PATH="/usr/local/go/bin:${HOME}/go/bin:/usr/bin:/bin:${PATH}"

cd "${REPO_ROOT}"
set -a
# shellcheck disable=SC1091
source "${REPO_ROOT}/.env"
set +a

chmod +x "${SCRIPT_DIR}/"*.sh 2>/dev/null || true

if ! command -v wrk >/dev/null 2>&1; then
  echo "需要 wrk"
  exit 1
fi

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

check_redis() {
  if command -v redis-cli >/dev/null 2>&1; then
    if redis-cli -h 127.0.0.1 -p 6379 ping 2>/dev/null | grep -qi PONG; then
      echo "Redis ok (redis-cli)"
      return 0
    fi
  fi
  if (echo PING | timeout 2 bash -c 'exec 3<>/dev/tcp/127.0.0.1/6379; cat >&3; cat <&3' 2>/dev/null | grep -qi PONG); then
    echo "Redis ok (tcp)"
    return 0
  fi
  # docker redis 已映射时 curl 不通，用 nc
  if timeout 2 bash -c 'echo >/dev/tcp/127.0.0.1/6379' 2>/dev/null; then
    echo "Redis 端口 6379 可达"
    return 0
  fi
  echo "Redis 127.0.0.1:6379 不可达；请先启动 compose redis"
  return 1
}

if [[ "${SKIP_SETUP}" != "1" ]]; then
  echo "== 停 Docker Gateway（改用本机 Go + Redis） =="
  docker stop firefly-gateway-1 >/dev/null 2>&1 || true

  if ! curl -sS -m 2 -o /dev/null -w "%{http_code}" "http://127.0.0.1:9001/api/user/1" | grep -qE '^[0-9]+$'; then
    echo "请先：docker compose -f deploy/docker-compose.yml -f bench/compose/docker-compose.bench.yml --env-file .env up -d"
    exit 1
  fi

  check_redis

  if ! command -v go >/dev/null 2>&1; then
    echo "需要 Go"
    exit 1
  fi

  if [[ "${FORCE_RESTART_GW}" == "1" ]] || ! curl -fsS -m 2 "${GO_BASE}/gateway/health" >/dev/null 2>&1; then
    echo "== 启动 Go Gateway（middleware + Redis） =="
    kill_port 8080
    (
      cd "${GW_DIR}"
      nohup go run ./cmd/gateway -config "${GW_CONFIG}" >"${GO_LOG}" 2>&1 &
      echo $! >/tmp/firefly-go-gateway-middleware.pid
    )
  else
    echo "Go Gateway 已在监听（FORCE_RESTART_GW=0）"
  fi
fi

wait_health "${GO_BASE}/gateway/health" "Go"

# 确认日志里是 redis token-bucket
if grep -q 'ratelimit=redis' "${GO_LOG}" 2>/dev/null; then
  echo "确认：${GO_LOG} 含 ratelimit=redis"
elif grep -q 'redis=' "${GO_LOG}" 2>/dev/null; then
  grep -E 'redis=|ratelimit=' "${GO_LOG}" | tail -n3 || true
fi

eval "$("${SCRIPT_DIR}/seed-bench.sh")"
: "${USER_ID:?}"
: "${NOTE_ID:?}"
: "${TOKEN:?}"

NPROC="$(nproc 2>/dev/null || echo '?')"
MEM="$(free -h 2>/dev/null | awk '/Mem:/{print $2}' || echo '?')"

run() {
  local title="$1" path="$2"
  if [[ -n "${_BENCH_NEED_COOLDOWN:-}" ]]; then
    echo "冷却 ${COOLDOWN_SEC}s ..."
    sleep "${COOLDOWN_SEC}"
  fi
  _BENCH_NEED_COOLDOWN=1
  echo
  echo "======== [Go] ${title} ========"
  echo "预热 ${path} (${WARMUP_SEC}) ..."
  wrk -t2 -c10 -d"${WARMUP_SEC}" -H "Authorization: Bearer ${TOKEN}" "${GO_BASE}${path}" >/dev/null 2>&1 || true
  echo "cmd: wrk -t${THREADS} -c${CONNECTIONS} -d${DURATION} --latency ..."
  wrk -t"${THREADS}" -c"${CONNECTIONS}" -d"${DURATION}" --latency \
    -H "Authorization: Bearer ${TOKEN}" "${GO_BASE}${path}"
}

{
  echo "Go 业务延迟 USER_ID=${USER_ID} NOTE_ID=${NOTE_ID} t=${THREADS} c=${CONNECTIONS} d=${DURATION}"
  echo "配置：${GW_CONFIG}"
  echo "中间件：Redis 限流开启；熔断关闭（避免 timeout 误触发致全 503）；限流阈值抬高以免 429 淹没 P99"
  echo
  curl -sS -o /dev/null -w "冒烟 user %{http_code}\n" -H "Authorization: Bearer ${TOKEN}" "${GO_BASE}/api/user/${USER_ID}" || true
  curl -sS -o /dev/null -w "冒烟 note %{http_code}\n" -H "Authorization: Bearer ${TOKEN}" "${GO_BASE}/api/note/${NOTE_ID}" || true
  curl -sS -o /dev/null -w "冒烟 like %{http_code}\n" -H "Authorization: Bearer ${TOKEN}" "${GO_BASE}/api/social/like/${NOTE_ID}/count" || true
  curl -sS -o /dev/null -w "冒烟 comment %{http_code}\n" -H "Authorization: Bearer ${TOKEN}" "${GO_BASE}/api/social/comment/${NOTE_ID}?page=1&size=20" || true
  curl -sS -o /dev/null -w "冒烟 notify %{http_code}\n" -H "Authorization: Bearer ${TOKEN}" "${GO_BASE}/api/notify/list?page=1&size=20" || true

  run "读用户资料" "/api/user/${USER_ID}"
  run "读笔记详情" "/api/note/${NOTE_ID}"
  run "赞数" "/api/social/like/${NOTE_ID}/count"
  run "评论列表" "/api/social/comment/${NOTE_ID}?page=1&size=20"
  run "通知列表" "/api/notify/list?page=1&size=20"
} | tee "${RAW}"

# 解析 wrk --latency：QPS / P50 / P99（统一转 ms）
# shellcheck disable=SC2016
python3 - "${RAW}" "${REPORT}" "${TS}" "${NPROC}" "${MEM}" "${THREADS}" "${CONNECTIONS}" "${DURATION}" "${USER_ID}" "${NOTE_ID}" "${GW_CONFIG}" <<'PY'
import re, sys
from pathlib import Path

raw_path, report_path = Path(sys.argv[1]), Path(sys.argv[2])
ts, nproc, mem = sys.argv[3], sys.argv[4], sys.argv[5]
threads, connections, duration = sys.argv[6], sys.argv[7], sys.argv[8]
user_id, note_id, gw_config = sys.argv[9], sys.argv[10], sys.argv[11]
text = raw_path.read_text(encoding="utf-8", errors="replace")

def to_ms(val: str, unit: str) -> float:
    v = float(val)
    u = unit.lower()
    if u.startswith("us"):
        return v / 1000.0
    if u.startswith("ms"):
        return v
    if u.startswith("s"):
        return v * 1000.0
    return v

blocks = re.split(r"======== \[Go\] ", text)
rows = []
for b in blocks[1:]:
    title = b.split(" ========", 1)[0].strip()
    qps_m = re.search(r"Requests/sec:\s+([0-9.]+)", b)
    p50_m = re.search(r"^\s*50%\s+([0-9.]+)(us|ms|s)", b, re.M)
    p99_m = re.search(r"^\s*99%\s+([0-9.]+)(us|ms|s)", b, re.M)
    avg_m = re.search(r"Latency\s+([0-9.]+)(us|ms|s)", b)
    non2xx_m = re.search(r"Non-2xx or 3xx responses:\s+(\d+)", b)
    if not (qps_m and p50_m and p99_m):
        continue
    qps = float(qps_m.group(1))
    p50 = to_ms(p50_m.group(1), p50_m.group(2))
    p99 = to_ms(p99_m.group(1), p99_m.group(2))
    avg = to_ms(avg_m.group(1), avg_m.group(2)) if avg_m else None
    non2xx = int(non2xx_m.group(1)) if non2xx_m else 0
    rows.append((title, qps, p50, p99, avg, non2xx))

def fmt_ms(x: float) -> str:
    if x >= 100:
        return f"{x:.1f}"
    if x >= 10:
        return f"{x:.2f}"
    return f"{x:.3f}"

lines = []
lines.append(f"# Go 业务延迟（中间件+Redis）{ts}")
lines.append("")
lines.append("| 项 | 值 |")
lines.append("|----|-----|")
lines.append(f"| CPU / 内存 | {nproc} / {mem} |")
lines.append(f"| wrk | `-t{threads} -c{connections} -d{duration} --latency` |")
lines.append(f"| 配置 | `{Path(gw_config).name}`（Redis 限流开启，阈值抬高） |")
lines.append(f"| USER_ID / NOTE_ID | {user_id} / {note_id} |")
lines.append("")
lines.append("| 接口 | QPS | Avg(ms) | P50(ms) | P99(ms) | Non-2xx |")
lines.append("|------|-----|---------|---------|---------|---------|")
for title, qps, p50, p99, avg, non2xx in rows:
    avg_s = fmt_ms(avg) if avg is not None else "n/a"
    lines.append(f"| {title} | {qps:.2f} | {avg_s} | {fmt_ms(p50)} | {fmt_ms(p99)} | {non2xx} |")

if rows:
    n = len(rows)
    avg_qps = sum(r[1] for r in rows) / n
    avg_p50 = sum(r[2] for r in rows) / n
    avg_p99 = sum(r[3] for r in rows) / n
    lines.append("")
    lines.append("## 五接口算术平均")
    lines.append("")
    lines.append("| 指标 | 值 |")
    lines.append("|------|-----|")
    lines.append(f"| 平均 QPS | **{avg_qps:.2f}** |")
    lines.append(f"| 平均 P50 | **{fmt_ms(avg_p50)} ms** |")
    lines.append(f"| 平均 P99 | **{fmt_ms(avg_p99)} ms** |")

lines.append("")
lines.append(f"原始：`bench/reports/biz-go-latency-{ts}.raw.txt`")
report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"报告：{report_path}")
if rows:
    print(f"平均 QPS={sum(r[1] for r in rows)/len(rows):.2f}  P50={sum(r[2] for r in rows)/len(rows):.3f}ms  P99={sum(r[3] for r in rows)/len(rows):.3f}ms")
else:
    print("解析失败：未找到 wrk Latency Distribution", file=sys.stderr)
    sys.exit(1)
PY

echo "报告：${REPORT}"
