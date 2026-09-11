#!/usr/bin/env bash
# 经 Go 网关：L1 关 vs 开 对照压测（补简历 P99）
#   THREADS=4 CONNECTIONS=50 DURATION=20s COOLDOWN_SEC=5 ./bench/scripts/bench-cache-l1-ab.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BENCH_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${BENCH_ROOT}/.." && pwd)"
REPORT_DIR="${BENCH_ROOT}/reports"
mkdir -p "${REPORT_DIR}"

THREADS="${THREADS:-4}"
CONNECTIONS="${CONNECTIONS:-50}"
DURATION="${DURATION:-20s}"
COOLDOWN_SEC="${COOLDOWN_SEC:-5}"
TS="$(date +%Y%m%d-%H%M%S)"
SUMMARY="${REPORT_DIR}/cache-l1-ab-${TS}.md"

cd "${REPO_ROOT}"
set -a
# shellcheck disable=SC1091
source "${REPO_ROOT}/.env"
set +a

COMPOSE=(docker compose
  -f deploy/docker-compose.yml
  -f bench/compose/docker-compose.bench.yml
  -f bench/compose/docker-compose.cache-l1.yml
  --env-file .env)

echo "== 编译四服务 =="
(cd services && mvn -pl user-service,content-service,social-service,notify-service -am package -DskipTests -q)

deploy_l1() {
  local enabled="$1"
  echo "== 部署 FIREFLY_CACHE_L1_ENABLED=${enabled} =="
  export FIREFLY_CACHE_L1_ENABLED="${enabled}"
  "${COMPOSE[@]}" up -d mysql redis rabbitmq elasticsearch \
    user-service content-service social-service notify-service feed-service media-service search-service
  # 强制带新 env 重建四服务
  "${COMPOSE[@]}" up -d --force-recreate user-service content-service social-service notify-service

  for svc in user-service content-service social-service notify-service; do
    docker cp "services/${svc}/target/${svc}-0.0.1-SNAPSHOT.jar" "firefly-${svc}-1:/app/app.jar"
    docker restart "firefly-${svc}-1" >/dev/null
  done

  for i in $(seq 1 60); do
    ok=1
    for pair in "user-service:9001" "content-service:9002" "social-service:9004" "notify-service:9005"; do
      name="${pair%%:*}"
      port="${pair##*:}"
      if ! docker exec "firefly-${name}-1" wget -q -O- -T 2 "http://127.0.0.1:${port}/actuator/health" 2>/dev/null | grep -q UP; then
        ok=0
        break
      fi
    done
    if [[ "${ok}" == "1" ]]; then
      echo "四服务 healthy (L1=${enabled})"
      docker logs firefly-social-service-1 2>&1 | grep -E 'CommentCache L1=|LikeService count L1=' | tail -n2 || true
      return 0
    fi
    sleep 2
  done
  echo "服务未就绪"
  exit 1
}
run_phase() {
  local tag="$1" enabled="$2"
  deploy_l1 "${enabled}"
  echo "== 压测 phase=${tag} 经 Go 网关 =="
  FORCE_RESTART_GW=1 \
  THREADS="${THREADS}" CONNECTIONS="${CONNECTIONS}" DURATION="${DURATION}" COOLDOWN_SEC="${COOLDOWN_SEC}" \
    "${SCRIPT_DIR}/bench-go-business-latency.sh" | tee "${REPORT_DIR}/cache-l1-ab-${TS}-${tag}.console.txt"
  # 最新 biz-go-latency 报告
  latest=$(ls -1t "${REPORT_DIR}"/biz-go-latency-*.md | head -n1)
  cp -f "${latest}" "${REPORT_DIR}/cache-l1-ab-${TS}-${tag}.md"
  cp -f "${latest%.md}.raw.txt" "${REPORT_DIR}/cache-l1-ab-${TS}-${tag}.raw.txt" 2>/dev/null || true
  echo "PHASE_REPORT_${tag}=${latest}"
}

run_phase "off" "false"
run_phase "on" "true"

python3 - "${REPORT_DIR}/cache-l1-ab-${TS}-off.md" "${REPORT_DIR}/cache-l1-ab-${TS}-on.md" "${SUMMARY}" "${THREADS}" "${CONNECTIONS}" "${DURATION}" <<'PY'
import re, sys
from pathlib import Path

def parse(path: Path):
    text = path.read_text(encoding="utf-8", errors="replace")
    rows = {}
    for line in text.splitlines():
        if not line.startswith("| ") or line.startswith("| 接口") or line.startswith("|----") or line.startswith("| 项"):
            continue
        parts = [p.strip() for p in line.strip("|").split("|")]
        if len(parts) < 5:
            continue
        name = parts[0]
        if name in ("读用户资料", "读笔记详情", "赞数", "评论列表", "通知列表"):
            try:
                rows[name] = {
                    "qps": float(parts[1]),
                    "p50": float(parts[3].replace("ms","")),
                    "p99": float(parts[4].replace("ms","")),
                }
            except ValueError:
                pass
    avg = text
    m = re.search(r"平均 QPS \| \*\*([0-9.]+)\*\*", text)
    p50 = re.search(r"平均 P50 \| \*\*([0-9.]+) ms\*\*", text)
    p99 = re.search(r"平均 P99 \| \*\*([0-9.]+) ms\*\*", text)
    return rows, float(m.group(1)) if m else None, float(p50.group(1)) if p50 else None, float(p99.group(1)) if p99 else None

off_rows, off_qps, off_p50, off_p99 = parse(Path(sys.argv[1]))
on_rows, on_qps, on_p50, on_p99 = parse(Path(sys.argv[2]))
out, threads, conns, dur = Path(sys.argv[3]), sys.argv[4], sys.argv[5], sys.argv[6]

lines = [
    f"# 缓存 L1 A/B（经 Go 网关）",
    "",
    f"| 项 | 值 |",
    f"|----|-----|",
    f"| wrk | `-t{threads} -c{conns} -d{dur}` + 接口冷却 |",
    f"| 变量 | `FIREFLY_CACHE_L1_ENABLED` false → true |",
    f"| 说明 | 保留 Gateway；关 L1=每次回源 Redis；开 L1=Caffeine |",
    "",
    "## 五接口算术平均",
    "",
    "| 指标 | L1 关（基线） | L1 开（优化） |",
    "|------|---------------|---------------|",
    f"| QPS | {off_qps:.2f} | **{on_qps:.2f}** |" if off_qps and on_qps else "| QPS | n/a | n/a |",
    f"| P50 | {off_p50:.2f} ms | **{on_p50:.2f} ms** |" if off_p50 and on_p50 else "| P50 | n/a | n/a |",
    f"| P99 | {off_p99:.2f} ms | **{on_p99:.2f} ms** |" if off_p99 and on_p99 else "| P99 | n/a | n/a |",
    "",
    "## 分接口 P99",
    "",
    "| 接口 | L1 关 P99 | L1 开 P99 |",
    "|------|-----------|-----------|",
]
for name in ("读用户资料", "读笔记详情", "赞数", "评论列表", "通知列表"):
    a = off_rows.get(name, {})
    b = on_rows.get(name, {})
    lines.append(f"| {name} | {a.get('p99', float('nan')):.2f} | {b.get('p99', float('nan')):.2f} |")

if off_qps and on_qps and off_p99 and on_p99:
    lines += [
        "",
        "## 简历可用数字（本轮）",
        "",
        f"- QPS：{off_qps:.0f} → {on_qps:.0f}",
        f"- P50：约 {on_p50:.0f} ms" if on_p50 else "",
        f"- P99：约 {off_p99:.0f} ms → **{on_p99:.0f} ms**",
    ]

Path(out).write_text("\n".join([x for x in lines if x is not None]) + "\n", encoding="utf-8")
print(f"汇总：{out}")
if on_p99 is not None and off_p99 is not None:
    print(f"RESUME_P99_OFF={off_p99:.0f} RESUME_P99_ON={on_p99:.0f} QPS={off_qps:.0f}->{on_qps:.0f}")
PY

echo "汇总：${SUMMARY}"
