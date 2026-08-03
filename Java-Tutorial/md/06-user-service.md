# 06 · user-service 逐步实现

## 本章目标

做出可用的用户服务（端口 **9001**）：

- 注册 / 登录
- 查看与修改资料
- 关注 / 取消关注
- 查看他人主页

前置：已完成第 03～05 章。

---

## 步骤 0：工程清单

| 项 | 值 |
|----|-----|
| 工程名 | `user-service` |
| 端口 | 9001 |
| 库 | `user` |
| 路径前缀 | `/api/user` |

包名建议：`com.firefly.user`

目录：

```
user-service/
  src/main/java/com/firefly/user/
    UserApplication.java
    common/ApiResponse.java
    entity/User.java
    entity/Follow.java
    dto/...
    mapper/UserMapper.java
    mapper/FollowMapper.java
    service/UserService.java
    service/FollowService.java
    controller/UserController.java
    controller/FollowController.java
    controller/HealthController.java
```

---

## 步骤 1：补全表结构

```sql
USE user;

-- 若第 05 章已建 user 表可跳过
CREATE TABLE IF NOT EXISTS `user` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `username`   VARCHAR(64)  NOT NULL UNIQUE,
  `password`   VARCHAR(128) NOT NULL,
  `nickname`   VARCHAR(64)  NOT NULL,
  `avatar_url` VARCHAR(512) DEFAULT NULL,
  `bio`        VARCHAR(256) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `follow` (
  `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
  `follower_id`  BIGINT NOT NULL COMMENT '粉丝',
  `followee_id`  BIGINT NOT NULL COMMENT '被关注者',
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_follow (`follower_id`, `followee_id`),
  KEY idx_followee (`followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

若已有 `user` 表缺 `bio`：

```sql
ALTER TABLE `user` ADD COLUMN `bio` VARCHAR(256) DEFAULT NULL;
```

---

## 步骤 2：接口清单（照着实现）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/user/register` | 注册 |
| POST | `/api/user/login` | 登录，返回用户信息（token 见第 13 章） |
| GET | `/api/user/me` | 当前用户（Header `X-User-Id`） |
| PUT | `/api/user/me` | 改昵称/头像/简介 |
| GET | `/api/user/{id}` | 他人主页 |
| POST | `/api/user/follow/{id}` | 关注 |
| DELETE | `/api/user/follow/{id}` | 取关 |
| GET | `/api/user/{id}/followers` | 粉丝列表（可后做） |
| GET | `/api/user/{id}/following` | 关注列表（可后做） |
| GET | `/health` | 健康检查 |

---

## 步骤 3：登录逻辑要点

```text
1. 按 username 查用户
2. 把入参 password 做同样哈希，与库中比较
3. 成功：返回 id、username、nickname、avatarUrl（不要返回密码）
4. 失败：code=40101，message=用户名或密码错误
```

新手阶段 token 可以：

- **方案 A（推荐配合网关）**：登录只校验账号；真正 JWT 由 Gateway 或单独 auth 发（第 13 章）。
- **方案 B（本服务临时）**：登录成功后返回一个简单 token 字符串（甚至先返回 `userId`），App 后续请求带 `X-User-Id`。

开发联调先用 **Header `X-User-Id: 1`** 模拟已登录，最省事。

---

## 步骤 4：从 Header 取当前用户

```java
@GetMapping("/me")
public ApiResponse<User> me(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
    if (userId == null) {
        return ApiResponse.fail(40100, "未登录");
    }
    User u = userService.findById(userId);
    if (u == null) {
        return ApiResponse.fail(40401, "用户不存在");
    }
    return ApiResponse.ok(u);
}
```

测试：

```powershell
curl http://127.0.0.1:9001/api/user/me -H "X-User-Id: 1"
```

---

## 步骤 5：关注 / 取关

业务规则：

1. 不能关注自己
2. 被关注用户必须存在
3. 重复关注：返回友好错误或直接成功（幂等）
4. 取关：删除 `follow` 行

`FollowMapper` 示例：

```java
@Mapper
public interface FollowMapper {

    @Insert("INSERT INTO follow(follower_id, followee_id) VALUES(#{followerId}, #{followeeId})")
    int insert(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    @Delete("DELETE FROM follow WHERE follower_id=#{followerId} AND followee_id=#{followeeId}")
    int delete(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    @Select("SELECT COUNT(1) FROM follow WHERE follower_id=#{followerId} AND followee_id=#{followeeId}")
    int exists(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);
}
```

需要在 `pom` 里保证 MyBatis 能识别 `@Param`（已有 starter 即可）。

---

## 步骤 6：修改资料

`PUT /api/user/me`，body 示例：

```json
{
  "nickname": "新昵称",
  "avatarUrl": "http://...",
  "bio": "热爱生活"
}
```

Service 里只更新非空字段；`username` 一般不允许改。

---

## 步骤 7：建议的自测顺序

1. 注册用户 A、用户 B  
2. 登录 A（或直接用 `X-User-Id`）  
3. A 关注 B  
4. 再关注一次 → 应提示已关注  
5. A 取关 B  
6. A 改昵称，再 GET `/me` 验证  

把请求保存在 Apifox 集合里，方便以后回归。

---

## 步骤 8：密码安全（最低要求）

- 至少 MD5/SHA（教程用）→ 尽快换成 **BCrypt**（`spring-security-crypto`）。
- 任何响应、日志都不要打印明文密码。

BCrypt 示例（了解即可）：

```java
// 依赖 spring-security-crypto 后
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
String hash = encoder.encode(rawPassword);
boolean match = encoder.matches(rawPassword, hash);
```

---

## 本章验收

- [ ] 注册写入 MySQL
- [ ] 登录能区分对错密码
- [ ] `X-User-Id` 能取到 `/me`
- [ ] 关注表有数据，取关后删除
- [ ] `/health` 返回 OK

下一章：笔记服务 → [07-content-service.md](./07-content-service.md)
