# FireFly 联调台（Vue）

本地可视化测试 user / media / content 三个服务。

## 启动

先启动后端（9001 / 9002 / 9003），再：

```powershell
cd d:\A_Software\Java\SAVE\FireFly-book\web-console
npm install
npm run dev
```

浏览器打开 http://127.0.0.1:5173

Vite 已代理：

| 前缀 | 后端 |
|------|------|
| `/api/user` | :9001 |
| `/api/note` | :9002 |
| `/api/media`、`/files` | :9003 |
