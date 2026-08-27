"""MySQL 笔记 → Elasticsearch 回填（ES 空索引时使用）。"""

from __future__ import annotations

import subprocess
import sys

from firefly_ai_common.clients.http_base import InternalHttpClient
from firefly_ai_common.config import load_settings


def main() -> None:
    mysql_pass = sys.argv[1] if len(sys.argv) > 1 else "123456"
    search_base = sys.argv[2] if len(sys.argv) > 2 else "http://127.0.0.1:9007"
    cfg = load_settings()

    sql = "SELECT id,user_id,title,content,cover_url FROM content.note WHERE status=1"
    cmd = [
        "docker",
        "exec",
        "firefly-mysql-1",
        "mysql",
        "--default-character-set=utf8mb4",
        "-uroot",
        f"-p{mysql_pass}",
        "-N",
        "-B",
        "-e",
        sql,
    ]
    raw = subprocess.check_output(cmd, stderr=subprocess.DEVNULL).decode("utf-8").strip()
    http = InternalHttpClient(
        search_base,
        secret=cfg.internal_hmac_secret,
        enabled=cfg.internal_auth_enabled,
    )
    ok = err = 0
    for line in raw.splitlines():
        parts = line.split("\t")
        if len(parts) < 5:
            continue
        body = {
            "id": int(parts[0]),
            "userId": int(parts[1]),
            "title": parts[2] or "",
            "content": parts[3] or "",
            "coverUrl": parts[4] or None,
        }
        try:
            http.post_json("/api/search/inner/index", body=body)
            ok += 1
        except Exception as exc:
            err += 1
            print(f"fail note {parts[0]}: {exc}", file=sys.stderr)
    http.close()
    print(f"notes indexed={ok} failed={err}")


if __name__ == "__main__":
    main()
