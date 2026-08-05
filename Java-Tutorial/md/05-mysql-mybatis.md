# 05 · MySQL + MyBatis（把数据存下来）

## 本章目标

- 建好 `user` 库和 `user` 表
- 在 Spring Boot 中用 MyBatis 完成注册与查询
- 理解：实体 Entity、Mapper 接口、XML（或注解 SQL）

建议：从本章起新建正式工程 **`user-service`**（端口 9001），或继续在 hello 上练，练完再迁移。

---

## 步骤 1：建库建表

在 MySQL 执行：

```sql
CREATE DATABASE IF NOT EXISTS user DEFAULT CHARACTER SET utf8mb4;
USE user;

CREATE TABLE IF NOT EXISTS `user` (
  `id`         BIGINT PRIMARY KEY AUTO_INCREMENT,
  `username`   VARCHAR(64)  NOT NULL UNIQUE,
  `password`   VARCHAR(128) NOT NULL,
  `nickname`   VARCHAR(64)  NOT NULL,
  `avatar_url` VARCHAR(512) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

用客户端确认表已存在。

---

## 步骤 2：pom.xml 增加依赖

在 `pom.xml` 的 `<dependencies>` 里加入（版本由 Spring Boot 父 POM 管理时可不写 version）：

```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.3</version>
</dependency>

<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

若用 https://start.spring.io/ 创建，可直接勾选：**MyBatis Framework**、**MySQL Driver**。

IDEA 右键 `pom.xml` → Maven → Reload Project。

---

## 步骤 3：配置数据源

`src/main/resources/application.yml`：

```yaml
server:
  port: 9001

spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/user?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456   # 改成你的密码
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis:
  configuration:
    map-underscore-to-camel-case: true   # user_name ↔ userName
  type-aliases-package: userservice.entity
```

密码务必改成你本机的。

---

## 步骤 4：实体类 Entity

```java
package userservice.entity;

import java.time.LocalDateTime;

public class User {
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String avatarUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 省略 getter/setter —— 请全部生成
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

`avatar_url` 列 ↔ `avatarUrl` 字段（靠 `map-underscore-to-camel-case`）。

---

## 步骤 5：Mapper 接口（注解 SQL，新手更直观）

```java
package userservice.mapper;

import userservice.entity.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user WHERE id = #{id}")
    User findById(Long id);

    @Select("SELECT * FROM user WHERE username = #{username}")
    User findByUsername(String username);

    @Insert("INSERT INTO user(username, password, nickname, avatar_url) " +
            "VALUES(#{username}, #{password}, #{nickname}, #{avatarUrl})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);
}
```

启动类上确保能扫到 Mapper，二选一：

- 启动类加 `@MapperScan("userservice.mapper")`
- 或每个 Mapper 已有 `@Mapper`（通常够用）

---

## 步骤 6：改造 Service 用数据库

```java
package userservice.service;

import userservice.entity.User;
import userservice.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User register(String username, String password, String nickname) {
        if (userMapper.findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User u = new User();
        u.setUsername(username);
        // 极简哈希，生产请用 BCrypt
        u.setPassword(DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8)));
        u.setNickname(nickname);
        userMapper.insert(u);
        u.setPassword(null); // 返回前抹掉密码
        return u;
    }

    public User findById(Long id) {
        User u = userMapper.findById(id);
        if (u != null) {
            u.setPassword(null);
        }
        return u;
    }
}
```

---

## 步骤 7：Controller 返回实体

沿用第 04 章的 `ApiResponse` + `RegisterRequest`，Service 改为上面的实现。重启后：

1. POST `/api/user/register`
2. 到 MySQL：`SELECT * FROM user;`
3. GET `/api/user/{id}`

能查到刚插入的行，说明整条链路通了。

---

## 步骤 8：分层全景（请背下来）

```
HTTP JSON
   ↓
UserController
   ↓
UserService          ← 业务：查重、加密、抹密码
   ↓
UserMapper           ← SQL
   ↓
MySQL 表 user
```

---

## 常见问题

| 现象 | 原因与处理 |
|------|------------|
| `Access denied` | 用户名密码错 |
| `Unknown database` | 没建 `user` |
| `Communications link failure` | MySQL 没启动或端口不对 |
| 插入成功但 Java 里 id 仍是 null | 检查 `@Options(useGeneratedKeys=true, keyProperty="id")` |
| 字段全是 null | 驼峰映射没开，或 getter/setter 缺失 |

---

## 本章验收

- [ ] 注册后数据库有行
- [ ] 按 id 查询与库中一致
- [ ] 响应里不包含 password
- [ ] 能说出 Controller / Service / Mapper 各自职责

下一章：把 user-service 按产品需求做完整 → [06-user-service.md](./06-user-service.md)
