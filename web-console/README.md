# FireFly 联调前端（小红书风）

响应式网页 UI：桌面顶栏 + 多列瀑布流；窄屏仍用底栏。已有后端能力直接接入；未实现能力标注「待实现」。

## 启动

1. MySQL / Redis / RabbitMQ  
2. Java 服务（9001–9005）  
3. Gateway：`cd Gateway/gateway && go run ./cmd/gateway`  
4. 前端：

```powershell
cd d:\A_Software\Java\SAVE\FireFly-book\web-console
npm install
npm run dev
```

浏览器打开 http://localhost:5173  

Vite 将 `/api`、`/files` 代理到 Gateway `:8080`。

## 已接入

| 模块 | 能力 |
|------|------|
| 用户 | 注册/登录 JWT、关注/取关、粉丝与关注列表、个人主页笔记 |
| 媒体 | 发布页上传封面 |
| 笔记 | 创建、详情、按用户列表 |
| 互动 | 点赞/取消、收藏/取消、评论列表与发表 |
| 通知 | 通知列表、已读 |
| 关注流 | `GET /api/feed/following`（feed-service 读扩散） |

发现页仍用「我的 + 关注」聚合；**关注**频道走独立 `feed-service`。

## 待实现（仅前端占位）

- 附近（LBS）
- 搜索 / 热榜（`search-service`）
- 全局推荐发现流（`feed-service`）
- 市集 / 电商
- 私信 IM
- 个人页：编辑资料、收藏夹、赞过列表、获赞总数
- 发布页：话题 / 地点 / @用户
