#!/usr/bin/env bash
# 单服务直连压测：只打 social-service 评论列表（HMAC），停网关与其它 Java
#   THREADS=4 CONNECTIONS=50 DURATION=60s ./bench/scripts/bench-social-comment-direct.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BENCH_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${BENCH_ROOT}/.." && pwd)"
REPORT_DIR="${BENCH_ROOT}/reports"
mkdir -p "${REPORT_DIR}"

THREADS="${THREADS:-4}"
CONNECTIONS="${CONNECTIONS:-50}"
DURATION="${DURATION:-60s}"
WARMUP_SEC="${WARMUP_SEC:-5s}"
NOTE_ID="${NOTE_ID:-98}"
USER_ID="${USER_ID:-15}"
BASE="${BASE:-http://127.0.0.1:9004}"
PATH_API="/api/social/comment/${NOTE_ID}?page=1&size=20"

TS_TAG="$(date +%Y%m%d-%H%M%S)"
REPORT="${REPORT_DIR}/social-comment-direct-${TS_TAG}.md"
RAW="${REPORT_DIR}/social-comment-direct-${TS_TAG}.raw.txt"

cd "${REPO_ROOT}"
set -a
# shellcheck disable=SC1091
source "${REPO_ROOT}/.env"
set +a

HMAC_SECRET="${INTERNAL_HMAC_SECRET:-}"
if [[ -z "${HMAC_SECRET}" ]]; then
  echo "需要 INTERNAL_HMAC_SECRET"
  exit 1
fi
if ! command -v wrk >/dev/null 2>&1; then
  echo "需要 wrk"
  exit 1
fi

sign_internal() {
  local uid="$1" ts="$2"
  printf '%s' "${uid}|${ts}" | openssl dgst -sha256 -hmac "${HMAC_SECRET}" | awk '{print $NF}'
}

echo "== 停网关与其它业务（保留 mysql/redis/rabbitmq/social） =="
docker stop firefly-gateway-1 >/dev/null 2>&1 || true
# 若本机还跑着 Go gateway
if command -v ss >/dev/null 2>&1; then
  pids=$(ss -ltnp 2>/dev/null | awk '$4 ~ /:8080$/ {print}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | sort -u)
  if [[ -n "${pids}" ]]; then
    # shellcheck disable=SC2086
    kill ${pids} 2>/dev/null || true
  fi
fi
for c in firefly-user-service-1 firefly-content-service-1 firefly-notify-service-1 \
         firefly-feed-service-1 firefly-media-service-1 firefly-search-service-1; do
  docker stop "$c" >/dev/null 2>&1 || true
done

echo "== 确保 social + redis + mysql（抬高 HMAC skew） =="
docker compose -f deploy/docker-compose.yml \
  -f bench/compose/docker-compose.bench.yml \
  -f bench/compose/docker-compose.social-solo.yml \
  --env-file .env up -d mysql redis rabbitmq social-service

# compose 可能因 depends_on 拉起 user/content；再停掉以腾出 CPU
for c in firefly-user-service-1 firefly-content-service-1 firefly-notify-service-1 \
         firefly-feed-service-1 firefly-media-service-1 firefly-search-service-1 \
         firefly-gateway-1; do
  docker stop "$c" >/dev/null 2>&1 || true
done

echo "等待 social health ..."
for i in $(seq 1 40); do
  if docker exec firefly-social-service-1 wget -q -O- -T 2 http://127.0.0.1:9004/actuator/health 2>/dev/null | grep -q UP; then
    echo "social ok"
    break
  fi
  sleep 2
  if [[ "$i" -eq 40 ]]; then
    echo "social 未就绪"
    docker logs firefly-social-service-1 --tail 30
    exit 1
  fi
done

TS="$(date +%s)"
SIGN="$(sign_internal "${USER_ID}" "${TS}")"
HDR=(-H "X-User-Id: ${USER_ID}" -H "X-Gateway-Ts: ${TS}" -H "X-Gateway-Sign: ${SIGN}")

echo "冒烟 ${BASE}${PATH_API}"
code=$(curl -sS -o /tmp/ff-comment-smoke.json -w "%{http_code}" "${HDR[@]}" "${BASE}${PATH_API}" || echo 000)
echo "http ${code} body=$(head -c 120 /tmp/ff-comment-smoke.json 2>/dev/null || true)"
if [[ "${code}" != "200" ]]; then
  exit 1
fi

NPROC="$(nproc 2>/dev/null || echo '?')"
MEM="$(free -h 2>/dev/null | awk '/Mem:/{print $2}' || echo '?')"

{
  echo "单服务直连评论 social:9004 NOTE_ID=${NOTE_ID} USER_ID=${USER_ID}"
  echo "环境：停网关/其它 Java；保留 mysql+redis+rabbitmq+social；HMAC skew=600"
  echo "wrk -t${THREADS} -c${CONNECTIONS} -d${DURATION} --latency"
  echo "CPU/MEM: ${NPROC} / ${MEM}"
  echo
  echo "预热 ${WARMUP_SEC} ..."
  wrk -t2 -c10 -d"${WARMUP_SEC}" --latency \
    -H "X-User-Id: ${USER_ID}" \
    -H "X-Gateway-Ts: ${TS}" \
    -H "X-Gateway-Sign: ${SIGN}" \
    "${BASE}${PATH_API}" >/dev/null 2>&1 || true

  # 预热后再签一次，覆盖正式段
  TS="$(date +%s)"
  SIGN="$(sign_internal "${USER_ID}" "${TS}")"

  echo "======== [Direct] 评论列表 ========"
  wrk -t"${THREADS}" -c"${CONNECTIONS}" -d"${DURATION}" --latency \
    -H "X-User-Id: ${USER_ID}" \
    -H "X-Gateway-Ts: ${TS}" \
    -H "X-Gateway-Sign: ${SIGN}" \
    "${BASE}${PATH_API}"
} | tee "${RAW}"

python3 - "${RAW}" "${REPORT}" "${TS_TAG}" "${NPROC}" "${MEM}" "${THREADS}" "${CONNECTIONS}" "${DURATION}" "${NOTE_ID}" <<'PY'
import re, sys
from pathlib import Path
raw, report = Path(sys.argv[1]), Path(sys.argv[2])
ts, nproc, mem = sys.argv[3], sys.argv[4], sys.argv[5]
threads, conns, dur, note_id = sys.argv[6], sys.argv[7], sys.argv[8], sys.argv[9]
text = raw.read_text(encoding="utf-8", errors="replace")

def to_ms(val, unit):
    v = float(val)
    u = unit.lower()
    if u.startswith("us"): return v / 1000.0
    if u.startswith("ms"): return v
    if u.startswith("s"): return v * 1000.0
    return v

qps = re.search(r"Requests/sec:\s+([0-9.]+)", text)
p50 = re.search(r"^\s*50%\s+([0-9.]+)(us|ms|s)", text, re.M)
p99 = re.search(r"^\s*99%\s+([0-9.]+)(us|ms|s)", text, re.M)
avg = re.search(r"Latency\s+([0-9.]+)(us|ms|s)", text)
non = re.search(r"Non-2xx or 3xx responses:\s+(\d+)", text)
to = re.search(r"timeout\s+(\d+)", text)

def fmt(x):
    return f"{x:.2f}" if x >= 10 else f"{x:.3f}"

lines = [
    f"# 单服务直连评论压测 {ts}",
    "",
    "| 项 | 值 |",
    "|----|-----|",
    f"| 目标 | social-service `:9004` 评论列表（无网关） |",
    f"| 环境 | 停网关与其它 Java；mysql+redis+social |",
    f"| CPU / 内存 | {nproc} / {mem} |",
    f"| wrk | `-t{threads} -c{conns} -d{dur} --latency` + HMAC |",
    f"| NOTE_ID | {note_id} |",
    "",
    "| QPS | Avg(ms) | P50(ms) | P99(ms) | Non-2xx | timeout |",
    "|-----|---------|---------|---------|---------|---------|",
]
if qps and p50 and p99:
    a = to_ms(avg.group(1), avg.group(2)) if avg else float("nan")
    lines.append(
        f"| {float(qps.group(1)):.2f} | {fmt(a)} | {fmt(to_ms(p50.group(1), p50.group(2)))} | "
        f"{fmt(to_ms(p99.group(1), p99.group(2)))} | {non.group(1) if non else 0} | {to.group(1) if to else 0} |"
    )
else:
    lines.append("| 解析失败 | | | | | |")
lines += ["", f"原始：`bench/reports/social-comment-direct-{ts}.raw.txt`"]
report.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"报告：{report}")
PY

echo "报告：${REPORT}"
