# moderation-service

消费 `note.created`（笔记入库为审核中），**文本规则 + vxlink/nsfw_detector HTTP 审图**，
回写 content（通过=`status=1` / 拒绝=`status=3`）。通过后由 content 发 `note.published`，
Feed/Search 再扩散与建索引（先审后发）。

## 流程

```text
create note (status=2 审核中) + Outbox note.created
  → evaluate_text（敏感词 / 启发式）
  → MediaClient 拉原图
  → POST http://nsfw-detector:3333/check
  → PATCH status=1|3 + note.moderated
  →（通过时）content 发 note.published → feed / search
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
