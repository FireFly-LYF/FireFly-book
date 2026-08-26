# moderation-service

消费 `note.created`，**文本规则 + vxlink/nsfw_detector HTTP 审图**，回写 content 并发布 `note.moderated`。

## 流程

```text
note.created
  → evaluate_text（敏感词 / 启发式）
  → MediaClient 拉原图
  → POST http://nsfw-detector:3333/check
  → PATCH status（拒绝时）+ note.moderated
```

## 图片审核

不再内嵌 TensorFlow / 本地 `.h5`，改为调用官方镜像：

```powershell
docker pull vxlink/nsfw_detector:v1.12
docker run -d -p 3333:3333 --name nsfw-detector vxlink/nsfw_detector:v1.12
```

或随 AI compose 一起启动（已包含 `nsfw-detector` 服务）。

| 环境变量 | 默认 | 说明 |
|----------|------|------|
| `NSFW_DETECTOR_URL` | `http://127.0.0.1:3333` | 检测服务地址 |
| `NSFW_THRESHOLD` | `0.5` | NSFW 分数阈值 |
| `MAX_IMAGES_PER_NOTE` | `9` | 单笔记最多审几张 |
| `IMAGE_MODERATION_ENABLED` | `true` | 图片审核总开关 |
| `MEDIA_BASE_URL` | `http://127.0.0.1:9003` | 内网读原图 |

## 启动

```powershell
# 仅检测服务
docker run -d -p 3333:3333 --name nsfw-detector vxlink/nsfw_detector:latest

# moderation-service（本机）
cd services-ai
pip install -e ./firefly-ai-common -e ./moderation-service
python -m moderation_service.main
```

默认端口：`9101`
