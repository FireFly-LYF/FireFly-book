# 共享路径（可选）
bench_init_paths() {
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[1]:-${BASH_SOURCE[0]}}")" && pwd)"
  BENCH_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
  REPO_ROOT="$(cd "${BENCH_ROOT}/.." && pwd)"
  REPORT_DIR="${BENCH_ROOT}/reports"
  GW_DIR="${REPO_ROOT}/Gateway/gateway"
  export SCRIPT_DIR BENCH_ROOT REPO_ROOT REPORT_DIR GW_DIR
  mkdir -p "${REPORT_DIR}"
}
