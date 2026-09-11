#!/usr/bin/env python3
"""Falconsai NSFW GPU/CPU single-image latency (in-process, no HTTP)."""
from __future__ import annotations

import argparse
import json
import statistics
import time
from pathlib import Path

import torch
from PIL import Image
from transformers import AutoModelForImageClassification, ViTImageProcessor


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


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--image", required=True)
    ap.add_argument("--model", default="Falconsai/nsfw_image_detection")
    ap.add_argument("--n", type=int, default=100)
    ap.add_argument("--warmup", type=int, default=20)
    ap.add_argument("--device", default="auto", choices=["auto", "cuda", "cpu"])
    args = ap.parse_args()

    if args.device == "auto":
        device = "cuda" if torch.cuda.is_available() else "cpu"
    else:
        device = args.device
        if device == "cuda" and not torch.cuda.is_available():
            raise SystemExit("requested cuda but torch.cuda.is_available() is False")

    img_path = Path(args.image)
    if not img_path.is_file():
        raise SystemExit(f"image not found: {img_path}")

    print(f"torch={torch.__version__} cuda_available={torch.cuda.is_available()} device={device}")
    if device == "cuda":
        print(f"gpu={torch.cuda.get_device_name(0)} capability={torch.cuda.get_device_capability(0)}")

    t0 = time.perf_counter()
    processor = ViTImageProcessor.from_pretrained(args.model)
    model = AutoModelForImageClassification.from_pretrained(args.model)
    model.to(device)
    model.eval()
    load_ms = (time.perf_counter() - t0) * 1000
    print(f"model_load_ms={load_ms:.1f}")

    image = Image.open(img_path).convert("RGB")
    inputs = processor(images=image, return_tensors="pt")
    inputs = {k: v.to(device) for k, v in inputs.items()}

    # warmup
    with torch.inference_mode():
        for i in range(args.warmup):
            if device == "cuda":
                torch.cuda.synchronize()
            t1 = time.perf_counter()
            out = model(**inputs)
            if device == "cuda":
                torch.cuda.synchronize()
            dt = (time.perf_counter() - t1) * 1000
            if i == 0:
                probs = torch.nn.functional.softmax(out.logits, dim=-1)[0]
                pred = int(probs.argmax().item())
                label = model.config.id2label.get(pred, str(pred))
                print(f"warmup0_ms={dt:.3f} label={label} score={float(probs[pred]):.4f}")

    samples: list[float] = []
    with torch.inference_mode():
        for _ in range(args.n):
            if device == "cuda":
                torch.cuda.synchronize()
            t1 = time.perf_counter()
            _ = model(**inputs)
            if device == "cuda":
                torch.cuda.synchronize()
            samples.append((time.perf_counter() - t1) * 1000)

    samples.sort()
    avg = statistics.fmean(samples)
    result = {
        "device": device,
        "gpu": torch.cuda.get_device_name(0) if device == "cuda" else None,
        "model": args.model,
        "image": str(img_path),
        "n": args.n,
        "warmup": args.warmup,
        "model_load_ms": round(load_ms, 1),
        "min_ms": round(samples[0], 3),
        "avg_ms": round(avg, 3),
        "p50_ms": round(percentile(samples, 50), 3),
        "p90_ms": round(percentile(samples, 90), 3),
        "p95_ms": round(percentile(samples, 95), 3),
        "p99_ms": round(percentile(samples, 99), 3),
        "max_ms": round(samples[-1], 3),
        "qps": round(1000.0 / avg, 2) if avg > 0 else None,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
