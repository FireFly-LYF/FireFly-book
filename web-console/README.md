# FireFly 联调台（Vue）

本地可视化测试：浏览器 → Vite → **Gateway :8080** → 各 Java 服务。

## 启动

1. MySQL / Redis / RabbitMQ  
2. Java 服务（9001–9005；feed/search 未实现时可忽略）  
3. Gateway：`cd Gateway/gateway && go run ./cmd/gateway`  
4. 联调台：

```powershell
cd d:\A_Software\Java\SAVE\FireFly-book\web-console
npm install
npm run dev
```

浏览器打开 http://localhost:5173

Vite 将 `/api`、`/files` 代理到 Gateway；Gateway 再按前缀转发：

| 前缀 | 后端 |
|------|------|
| `/api/user` | :9001 |
| `/api/note` | :9002 |
| `/api/media`、`/files` | :9003 |
| `/api/social` | :9004 |
| `/api/notify` | :9005 |

## 建议自测路径

1. 注册用户 A、B  
2. A 发笔记 → B 点赞/评论  
3. A 在「通知」页刷新  
4. 也可直接访问 `http://127.0.0.1:8080/api/user/me`（Header `X-User-Id`）
