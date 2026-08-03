# 08 · media-service 逐步实现

## 本章目标

**media-service**（端口 **9003**）：接收图片上传，返回可访问的 URL，供发笔记使用。

新手先用 **本地磁盘**；以后再换成 OSS/MinIO。

---

## 步骤 0：工程

| 项 | 值 |
|----|-----|
| 端口 | 9003 |
| 库 | `media` |
| 前缀 | `/api/media` |

依赖：Web、MyBatis、MySQL。上传需要能接收 `multipart/form-data`（Spring Web 已支持）。

---

## 步骤 1：建表

```sql
CREATE DATABASE IF NOT EXISTS media DEFAULT CHARACTER SET utf8mb4;
USE media;

CREATE TABLE IF NOT EXISTS `media_asset` (
  `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id`      BIGINT       NOT NULL,
  `original_name` VARCHAR(256) NOT NULL,
  `content_type` VARCHAR(128) DEFAULT NULL,
  `size_bytes`   BIGINT       NOT NULL,
  `url`          VARCHAR(512) NOT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 步骤 2：配置上传目录

`application.yml`：

```yaml
server:
  port: 9003

spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 12MB
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/media?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456

media:
  storage-dir: D:/FireFlyData/media   # Windows 示例，按需修改
  public-base-url: http://127.0.0.1:9003/files
```

手动创建文件夹 `D:\FireFlyData\media`。

用 `@ConfigurationProperties` 或 `@Value` 读取：

```java
@Value("${media.storage-dir}")
private String storageDir;

@Value("${media.public-base-url}")
private String publicBaseUrl;
```

---

## 步骤 3：保存文件的核心逻辑

```text
1. 校验：必须是图片（contentType 以 image/ 开头）
2. 生成新文件名：UUID + 原扩展名，避免重名与中文路径问题
3. 保存到 storage-dir
4. 拼 url = public-base-url + "/" + 新文件名
5. 写入 media_asset 表
6. 返回 { id, url }
```

伪代码：

```java
String ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
String filename = UUID.randomUUID() + ext;
Path dest = Paths.get(storageDir, filename);
Files.createDirectories(dest.getParent());
file.transferTo(dest.toFile());
String url = publicBaseUrl + "/" + filename;
```

---

## 步骤 4：对外提供静态访问

让浏览器能打开 `http://127.0.0.1:9003/files/xxx.jpg`：

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${media.storage-dir}")
    private String storageDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // file:/// 注意斜杠；Windows 也可用 file:D:/FireFlyData/media/
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + storageDir + "/");
    }
}
```

若打不开图片，优先检查：路径是否带尾部 `/`、文件是否真的写到该目录。

---

## 步骤 5：上传接口

```java
@PostMapping("/upload")
public ApiResponse<?> upload(
        @RequestHeader("X-User-Id") Long userId,
        @RequestParam("file") MultipartFile file) {
    // 调用 MediaService.save(...)
}
```

Apifox 测试：Body 选 form-data，字段名 `file`，类型 File；Header 加 `X-User-Id`。

curl 示例：

```powershell
curl -X POST http://127.0.0.1:9003/api/media/upload `
  -H "X-User-Id: 1" `
  -F "file=@C:\Users\你\Pictures\test.jpg"
```

---

## 步骤 6：查询接口

`GET /api/media/{id}` → 返回 url、大小、上传者。

---

## 步骤 7：和发笔记串起来（里程碑）

```text
1. 上传 1～9 张图，拿到 urls
2. POST content-service /api/note，带上 mediaUrls 与 coverUrl
3. GET 笔记详情，确认图片地址能在浏览器打开
```

做到这里，**P0 主链路打通**。

---

## 步骤 8：以后升级方向（现在只了解）

| 现状 | 生产常见做法 |
|------|----------------|
| 文件经 Java 服务上传 | 客户端直传 OSS，服务只发签名 |
| 本地磁盘 | MinIO / 阿里云 OSS + CDN |
| 无缩略图 | 异步转码、多尺寸 |

大文件 **不要** 长期经 Gateway 裸传；第 13 章会提到。

---

## 本章验收

- [ ] 上传成功返回 url
- [ ] 浏览器能打开该 url 看到图片
- [ ] 数据库有 media_asset 记录
- [ ] 能与 content 发笔记联调

下一章：赞评藏 → [09-social-service.md](./09-social-service.md)
