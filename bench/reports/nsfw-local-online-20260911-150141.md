# NSFW local online path (HTTP /check, no Docker)

* time: 2026-09-11 15:01:41
* model: `Falconsai/nsfw_image_detection`
* device: **cuda**
* gpu: NVIDIA GeForce RTX 5060 Laptop GPU
* host_cpu: Intel(R) Core(TM) i7-14650HX
* conda_env: `pytorch`
* image: `cat.jpg` (89.1 KB)
* batch_size: 1
* input_size: 224
* TensorRT/ONNX: **False**
* params: N=100 Warmup=10 Concurrency=1,2
* note: 本机进程提供 `/check`，路径对齐 moderation（multipart 上传 + 预处理 + 推理 + JSON），**无 Docker**

## HTTP e2e

| c | ok | fail | QPS | min | avg | P50 | P90 | P95 | **P99** | max |
|---|---:|-----:|----:|----:|----:|----:|----:|----:|--------:|----:|
| 1 | 100 | 0 | 28.31 | 20.786 | 35.237 | 33.588 | 46.736 | 47.494 | **62.284** | 64.813 |
| 2 | 100 | 0 | 54.83 | 21.354 | 36.156 | 34.59 | 49.796 | 55.03 | **59.355** | 64.333 |

## In-process e2e（同模型，无 HTTP；含 decode+preprocess+forward）

| metric | ms |
|--------|---:|
| P50 | 19.63 |
| P95 | 20.893 |
| **P99** | **21.357** |
| avg | 19.67 |

* model_load_ms: 74910.7
* Raw: terminal capture `2026-09-11T07:00:06` / script `bench/scripts/bench_nsfw_local_online.py`

## 口径对照（面试用）

| 口径 | P99 | 说明 |
|------|-----|------|
| Docker CPU HTTP（旧） | ~393 ms | `vxlink/nsfw_detector` 容器 |
| 本机 GPU HTTP 线上同路径（本报告） | **~62 ms** | multipart + 预处理 + 推理 |
| 本机 GPU 进程内 e2e | ~21 ms | decode+preprocess+forward，无 HTTP |
| 本机 GPU 纯 forward（旧） | ~17 ms | 预处理在计时外 |
