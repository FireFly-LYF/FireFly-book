#!/usr/bin/env python3
"""本机 NSFW /check 服务 + 线上同路径压测（无 Docker）。

对齐 moderation-service：POST multipart file → JSON
{status,result:{nsfw,normal}}。

计时包含：multipart 解析 + 读图 + ViT 预处理 + GPU forward + JSON。
"""
from __future__ import annotations

import argparse
import json
import re
import statistics
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
from pathlib import Path
from typing import Any

import torch
from PIL import Image
from transformers import AutoModelForImageClassification, ViTImageProcessor

MODEL_ID = "Falconsai/nsfw_image_detection"


def percentile(sorted_vals: list[float], p: float) -> float:
    if not sorted_vals:
        return float("nan")
    if len(sorted_vals) == 1:
        return sorted_vals[0]
    rank = (p / 100.0) * (len(sorted_vals) - 1)
    lo = int(rank)
    hi = min(lo + 1, len(sorted_vals) - 1)
    w = rank - lo
    return sorted_vals[lo] * (1 - w) + sorted_vals[hi] * w


def extract_file_bytes(content_type: str, body: bytes) -> bytes:
    """从 multipart/form-data 取出 file 字段原始字节。"""
    m = re.search(r"boundary=([^;]+)", content_type or "", flags=re.I)
    if not m:
        # 非 multipart：整包当图片
        return body
    boundary = m.group(1).strip().strip('"').encode()
    parts = body.split(b"--" + boundary)
    for part in parts:
        if b"Content-Disposition:" not in part:
            continue
        header, _, payload = part.partition(b"\r\n\r\n")
        if b'name="file"' not in header and b"name=file" not in header:
            # 兼容无 name、仅 filename
            if b"filename=" not in header:
                continue
        data = payload
        if data.endswith(b"\r\n"):
            data = data[:-2]
        if data.endswith(b"--"):
            data = data[:-2]
        if data.endswith(b"\r\n"):
            data = data[:-2]
        if data:
            return data
    raise ValueError("multipart 中未找到 file 字段")


class Detector:
    def __init__(self, device: str, model_id: str = MODEL_ID) -> None:
        self.device = device
        self.processor = ViTImageProcessor.from_pretrained(model_id)
        self.model = AutoModelForImageClassification.from_pretrained(model_id)
        self.model.to(device)
        self.model.eval()
        self.lock = threading.Lock()
        # ViT 默认 size（用于报告）
        size = getattr(self.processor, "size", None)
        if isinstance(size, dict):
            self.input_size = size.get("height") or size.get("shortest_edge") or 224
        else:
            self.input_size = int(size) if size else 224

    def score_bytes(self, data: bytes) -> dict[str, float]:
        image = Image.open(BytesIO(data)).convert("RGB")
        inputs = self.processor(images=image, return_tensors="pt")
        inputs = {k: v.to(self.device) for k, v in inputs.items()}
        with self.lock:
            with torch.inference_mode():
                if self.device == "cuda":
                    torch.cuda.synchronize()
                out = self.model(**inputs)
                if self.device == "cuda":
                    torch.cuda.synchronize()
                probs = torch.nn.functional.softmax(out.logits, dim=-1)[0]
        # Falconsai: id2label 通常 normal/nsfw
        scores = {
            str(self.model.config.id2label[i]).lower(): float(probs[i].item())
            for i in range(probs.shape[0])
        }
        nsfw = scores.get("nsfw", scores.get("porn", 0.0))
        normal = scores.get("normal", scores.get("safe", 1.0 - nsfw))
        return {"nsfw": nsfw, "normal": normal}


def make_handler(detector: Detector):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt: str, *args: Any) -> None:
            return

        def do_POST(self) -> None:  # noqa: N802
            if self.path.rstrip("/") != "/check":
                self.send_error(404)
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                body = self.rfile.read(length)
                ct = self.headers.get("Content-Type", "")
                raw = extract_file_bytes(ct, body)
                result = detector.score_bytes(raw)
                payload = {"status": "success", "result": result}
                data = json.dumps(payload).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
            except Exception as exc:  # noqa: BLE001
                msg = json.dumps({"status": "error", "message": str(exc)}).encode()
                self.send_response(500)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(msg)))
                self.end_headers()
                self.wfile.write(msg)

        def do_GET(self) -> None:  # noqa: N802
            if self.path.rstrip("/") in ("", "/", "/health"):
                data = b'{"ok":true}'
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
                return
            self.send_error(404)

    return Handler


def post_check(url: str, image_bytes: bytes, filename: str) -> tuple[int, float, str]:
    import urllib.error
    import urllib.request

    boundary = "----ffbenchboundary"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f"Content-Type: application/octet-stream\r\n\r\n"
    ).encode() + image_bytes + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            code = resp.status
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        code = exc.code
    ms = (time.perf_counter() - t0) * 1000
    return code, ms, raw


def summarize(samples: list[float], wall_sec: float) -> dict[str, float]:
    s = sorted(samples)
    avg = statistics.fmean(s) if s else float("nan")
    return {
        "ok": len(s),
        "avg_ms": round(avg, 3),
        "min_ms": round(s[0], 3) if s else float("nan"),
        "p50_ms": round(percentile(s, 50), 3),
        "p90_ms": round(percentile(s, 90), 3),
        "p95_ms": round(percentile(s, 95), 3),
        "p99_ms": round(percentile(s, 99), 3),
        "max_ms": round(s[-1], 3) if s else float("nan"),
        "qps": round(len(s) / wall_sec, 2) if wall_sec > 0 else 0.0,
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--image", required=True)
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=3333)
    ap.add_argument("--n", type=int, default=100)
    ap.add_argument("--warmup", type=int, default=10)
    ap.add_argument("--concurrency", default="1")
    ap.add_argument("--device", default="auto", choices=["auto", "cuda", "cpu"])
    ap.add_argument("--model", default=MODEL_ID)
    ap.add_argument("--serve-only", action="store_true")
    ap.add_argument("--out-json", default="")
    args = ap.parse_args()

    if args.device == "auto":
        device = "cuda" if torch.cuda.is_available() else "cpu"
    else:
        device = args.device
        if device == "cuda" and not torch.cuda.is_available():
            raise SystemExit("cuda 不可用")

    img_path = Path(args.image)
    if not img_path.is_file():
        raise SystemExit(f"image not found: {img_path}")
    image_bytes = img_path.read_bytes()

    print(
        f"loading model device={device} "
        f"gpu={torch.cuda.get_device_name(0) if device == 'cuda' else None}"
    )
    t_load = time.perf_counter()
    detector = Detector(device=device, model_id=args.model)
    load_ms = (time.perf_counter() - t_load) * 1000
    print(f"model_load_ms={load_ms:.1f} input_size={detector.input_size}")

    handler = make_handler(detector)
    server = ThreadingHTTPServer((args.host, args.port), handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    url = f"http://{args.host}:{args.port}/check"
    print(f"local /check ready: {url}")

    if args.serve_only:
        print("serve-only: Ctrl+C to stop")
        try:
            while True:
                time.sleep(3600)
        except KeyboardInterrupt:
            pass
        finally:
            server.shutdown()
        return

    levels = [int(x) for x in re.split(r"[,;\s]+", args.concurrency) if x.strip()]
    if not levels:
        levels = [1]

    # warmup
    for _ in range(args.warmup):
        code, _, _ = post_check(url, image_bytes, img_path.name)
        if code != 200:
            raise SystemExit(f"warmup failed http={code}")

    results: dict[str, Any] = {
        "mode": "local-online-http",
        "note": "no Docker; HTTP multipart /check like moderation-service",
        "device": device,
        "gpu": torch.cuda.get_device_name(0) if device == "cuda" else None,
        "model": args.model,
        "input_size": detector.input_size,
        "batch_size": 1,
        "tensorrt_onnx": False,
        "image": str(img_path),
        "image_bytes": len(image_bytes),
        "model_load_ms": round(load_ms, 1),
        "n": args.n,
        "warmup": args.warmup,
        "url": url,
        "by_concurrency": {},
    }

    for c in levels:
        samples: list[float] = []
        fails = 0
        wall0 = time.perf_counter()
        with ThreadPoolExecutor(max_workers=c) as pool:
            futs = [
                pool.submit(post_check, url, image_bytes, img_path.name)
                for _ in range(args.n)
            ]
            for fut in as_completed(futs):
                code, ms, _ = fut.result()
                if code == 200:
                    samples.append(ms)
                else:
                    fails += 1
        wall = time.perf_counter() - wall0
        stats = summarize(samples, wall)
        stats["fail"] = fails
        stats["concurrency"] = c
        results["by_concurrency"][str(c)] = stats
        print(json.dumps({"concurrency": c, **stats}, ensure_ascii=False))

    # 同进程内对照：不含 HTTP，但仍含 decode+preprocess+forward（更接近算子侧）
    inproc: list[float] = []
    for _ in range(min(args.n, 50)):
        t0 = time.perf_counter()
        detector.score_bytes(image_bytes)
        inproc.append((time.perf_counter() - t0) * 1000)
    results["inprocess_e2e"] = summarize(inproc, sum(inproc) / 1000.0)

    print(json.dumps(results, ensure_ascii=False, indent=2))
    if args.out_json:
        Path(args.out_json).write_text(
            json.dumps(results, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
    server.shutdown()


if __name__ == "__main__":
    main()
