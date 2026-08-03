# 04 · 写 REST API（统一返回与分层）

## 本章目标

- 用 JSON 收发数据
- 学会 GET / POST、路径参数、请求体
- 建立：Controller → Service 分层
- 定好全项目统一的返回格式 `ApiResponse`

可在 `hello-service` 上练习，或新建 `user-service` 空壳练手。

---

## 步骤 1：统一返回格式

以后所有接口尽量长这样：

```json
{
  "code": 0,
  "message": "ok",
  "data": { }
}
```

`code = 0` 表示成功，非 0 表示业务失败。

新建 `com.firefly.helloservice.common.ApiResponse`：

```java
package com.firefly.helloservice.common;

public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 0;
        r.message = "ok";
        r.data = data;
        return r;
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = code;
        r.message = message;
        r.data = null;
        return r;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
}
```

`<T>` 表示 data 可以是任意类型。

---

## 步骤 2：DTO——请求/响应小对象

注册请求体示例：

```java
package com.firefly.helloservice.dto;

public class RegisterRequest {
    private String username;
    private String password;
    private String nickname;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
```

Spring 会把 JSON 自动填进这些字段（字段名要和 JSON 键一致）。

---

## 步骤 3：Service 层（业务）

```java
package com.firefly.helloservice.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserService {

    private final AtomicLong seq = new AtomicLong(1);
    private final Map<String, Map<String, Object>> users = new HashMap<>();

    public Map<String, Object> register(String username, String password, String nickname) {
        if (users.containsKey(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        Map<String, Object> u = new HashMap<>();
        u.put("id", seq.getAndIncrement());
        u.put("username", username);
        u.put("nickname", nickname);
        // 注意：真实项目密码要加密，这里仅演示
        u.put("password", password);
        users.put(username, u);

        Map<String, Object> safe = new HashMap<>();
        safe.put("id", u.get("id"));
        safe.put("username", username);
        safe.put("nickname", nickname);
        return safe;
    }

    public Map<String, Object> findById(Long id) {
        for (Map<String, Object> u : users.values()) {
            if (id.equals(u.get("id"))) {
                Map<String, Object> safe = new HashMap<>();
                safe.put("id", u.get("id"));
                safe.put("username", u.get("username"));
                safe.put("nickname", u.get("nickname"));
                return safe;
            }
        }
        return null;
    }
}
```

`@Service`：告诉 Spring「这是 Bean，可以被注入」。  
这里先用内存 Map，下一章再换 MySQL。

---

## 步骤 4：Controller

```java
package com.firefly.helloservice.controller;

import com.firefly.helloservice.common.ApiResponse;
import com.firefly.helloservice.dto.RegisterRequest;
import com.firefly.helloservice.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    // 构造器注入（推荐写法）
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        try {
            Map<String, Object> user = userService.register(
                    req.getUsername(), req.getPassword(), req.getNickname());
            return ApiResponse.ok(user);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(40001, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getById(@PathVariable Long id) {
        Map<String, Object> user = userService.findById(id);
        if (user == null) {
            return ApiResponse.fail(40401, "用户不存在");
        }
        return ApiResponse.ok(user);
    }
}
```

注解对照：

| 注解 | 作用 |
|------|------|
| `@RequestMapping("/api/user")` | 类上的公共前缀 |
| `@PostMapping` | POST |
| `@GetMapping("/{id}")` | GET，路径变量 |
| `@RequestBody` | 从 JSON 体绑定 |
| `@PathVariable` | 从 URL 路径取 `{id}` |

---

## 步骤 5：用 curl / Apifox 测试

注册：

```powershell
curl -X POST http://127.0.0.1:8081/api/user/register `
  -H "Content-Type: application/json" `
  -d "{\"username\":\"xiaohong\",\"password\":\"123456\",\"nickname\":\"小红\"}"
```

查询：

```powershell
curl http://127.0.0.1:8081/api/user/1
```

重复注册同名应返回 `code` 非 0。

---

## 步骤 6：分层记住这句话

```
Controller：只负责「收参数、调 Service、返回 ApiResponse」
Service：负责「业务规则」（重名校验、权限等）
以后 Mapper：只负责「SQL / 数据库」
```

Controller 里不要写一长串业务判断——后面会很难维护。

---

## 步骤 7：健康检查保留

网关以后会探活，保留：

```java
@GetMapping("/health")
public String health() {
    return "OK";
}
```

可放在单独 `HealthController`，路径 `/health`。

---

## 练习

增加 `POST /api/user/login`：body 含 username、password；成功返回用户信息（仍不返回密码）；失败返回错误码。

---

## 本章验收

- [ ] POST 注册返回 JSON，含 `code/message/data`
- [ ] GET 按 id 查询成功
- [ ] 理解 `@RequestBody` 与 `@PathVariable`
- [ ] Controller 与 Service 已分开

下一章：真正落库 → [05-mysql-mybatis.md](./05-mysql-mybatis.md)
