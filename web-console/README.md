# FireFly 联调前端（小红书风）

响应式网页 UI：桌面顶栏 + 多列瀑布流；窄屏仍用底栏。

## 启动

### Docker（推荐）

全栈 Compose 已包含 `web-console`，映射 **5173→80**。构建启动后浏览器打开 http://localhost:5173/（或在 Docker Desktop 点端口）。Nginx 将 `/api`、`/files` 反代到 Gateway。

### 本机热更新

1. MySQL / Redis / RabbitMQ / ES  
2. Java 服务（9001–9008）  
3. Gateway：`cd Gateway/gateway && go run ./cmd/gateway`  
4. 前端：

```powershell
cd d:\A_Software\Java\SAVE\FireFly-book\web-console
npm install
npm run dev
```

浏览器打开 http://localhost:5173  

Vite 将 `/api`、`/files` 代理到 Gateway `:8080`。与 Docker 前端同端口时勿同时占用 5173。

## 已接入

| 模块 | 能力 |
|------|------|
| 用户 | 注册/登录 JWT、关注/取关、粉丝与关注列表、他人主页 |
| 媒体 | 发布页上传封面 |
| 笔记 | 创建、详情、按用户列表 |
| 互动 | 点赞/取消、收藏/取消、赞过与收藏列表、评论楼中楼（含作者昵称头像） |
| 发布/评论 | `Idempotency-Key` 防重复提交 |
| 通知 | 通知列表、已读 |
| 关注流 | `GET /api/feed/following`（feed-service 写扩散时间线） |
| 搜索 | 笔记/用户搜索 + AI 智能回答（引用溯源，需 assistant-service） |
| 市集 | 商品浏览/上架、购物车、地址、下单、模拟支付、发货/确认收货/售后 |

## 演示账号

```powershell
# 先有种子笔记（及审核通过）后：
.\deploy\scripts\seed-demo-test.ps1
```

登录 **test / test**（登录页可一键填入）。

## 附近（南京）

首页「附近」：浏览器定位，失败则用 **南京·新街口**；`GET /api/feed/nearby`。  
造数：`.\deploy\scripts\seed-nearby-nanjing.ps1`。发布页可选南京地点或当前位置。

## 占位中 / 建设中

- 私信 IM（Outbox）— 下一阶段
- 发布页：话题 / @用户
- 个人页获赞总数
