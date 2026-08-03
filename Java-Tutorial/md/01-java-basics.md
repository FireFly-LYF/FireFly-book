# 01 · Java 最小必要知识（够用版）

> 目标不是成为语法专家，而是：**能看懂后面 Spring 代码里出现的东西**。  
> 有编程基础（例如你写过 Go）会更快；没有也没关系，跟着敲。

## 本章目标

- 理解：类、对象、方法、包
- 认识：基本类型、String、List、Map
- 会写：简单类 + `main` 跑起来
- 知道：接口、注解大概是什么（Spring 大量用注解）

预计：1～2 天。

---

## 步骤 1：程序长什么样

Java 代码必须写在 **类（class）** 里。入口方法叫 `main`。

创建文件 `Hello.java`：

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("你好，FireFly");
    }
}
```

含义速查：

| 词 | 含义 |
|----|------|
| `public` | 公开，别的地方也能用 |
| `class Hello` | 定义一个叫 Hello 的类，文件名通常也叫 `Hello.java` |
| `static` | 不创建对象也能调用（main 必须这样） |
| `void` | 没有返回值 |
| `String[] args` | 命令行参数 |
| `System.out.println` | 打印一行 |

编译与运行（有 JDK 后）：

```bash
javac Hello.java
java Hello
```

用 IDEA 时点绿色三角即可，不必每次手敲。

---

## 步骤 2：变量与类型

```java
int age = 20;                 // 整数
long userId = 10001L;         // 更大的整数（ID 常用 long）
double price = 9.9;           // 小数
boolean ok = true;            // 布尔
String name = "小红";         // 字符串（注意大写 S，是类）
```

和 Go 的粗略对比：

| Java | Go（若你熟悉） |
|------|----------------|
| `String` | `string` |
| `boolean` | `bool` |
| `int` / `long` | `int` / `int64` |
| 必须写在 class 里 | 可以 package 级函数 |

---

## 步骤 3：方法（函数）

```java
public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) {
        Calculator c = new Calculator(); // new = 创建对象
        int sum = c.add(1, 2);
        System.out.println(sum); // 3
    }
}
```

- `new Calculator()`：根据类创建 **对象（实例）**
- `c.add(...)`：调用对象的方法

---

## 步骤 4：包（package）

项目一大，类要分目录。第一行常写：

```java
package com.firefly.user.controller;
```

对应文件夹大致是：`src/main/java/com/firefly/user/controller/`。

别的文件要用这个类时：

```java
import com.firefly.user.controller.UserController;
```

Spring 项目里包名你会经常看到，记住：**包名 ↔ 文件夹路径** 即可。

---

## 步骤 5：常用集合

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

List<String> tags = new ArrayList<>();
tags.add("旅行");
tags.add("美食");

Map<String, Object> user = new HashMap<>();
user.put("id", 1L);
user.put("name", "小红");
```

- `List`：有序列表（类似动态数组）
- `Map`：键值对（类似字典）
- `<String>`：泛型，表示里面装什么类型

接口返回 JSON 时，后端经常用 `Map` 或专门的 DTO 类。

---

## 步骤 6：类字段与封装（后面实体会用到）

```java
public class User {
    private Long id;       // private：外面不能直接改
    private String nickname;

    public Long getId() {          // getter
        return id;
    }

    public void setId(Long id) {   // setter
        this.id = id;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
```

数据库一行记录 ↔ 一个 Java 对象，字段通过 get/set 读写。  
（后面可用 Lombok 的 `@Data` 少写这些样板代码。）

---

## 步骤 7：接口（interface）——先建立印象

```java
public interface UserService {
    User findById(Long id);
}
```

接口只规定「有哪些方法」，不写具体实现。Spring 里经常是：

- `UserService`（接口）
- `UserServiceImpl`（实现类）

新手阶段：**看到 interface 知道是「约定」就行**，实现细节后面跟着抄。

---

## 步骤 8：注解（Annotation）——Spring 的核心写法

注解就是贴在类/方法上的「标记」，例如：

```java
@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "ok";
    }
}
```

含义（现在只需混个脸熟）：

| 注解 | 大概意思 |
|------|----------|
| `@RestController` | 这是一个返回 JSON/文本的 Web 控制器 |
| `@GetMapping` | 处理 GET 请求 |
| `@Service` | 这是业务逻辑类，交给 Spring 管理 |
| `@Autowired` | 请 Spring 自动注入依赖 |

**你不用自己实现注解**，会用框架提供的即可。

---

## 步骤 9：异常（出错时）

```java
try {
    int x = 1 / 0;
} catch (Exception e) {
    System.out.println("出错了: " + e.getMessage());
}
```

业务里常见：用户不存在就 `throw new RuntimeException("用户不存在")`，或自定义业务异常，再在统一异常处理里变成 JSON 错误码。

---

## 步骤 10：和本项目的对应关系

| 你以后写的 | Java 概念 |
|------------|-----------|
| `User` 实体类 | class + 字段 |
| `UserController` | 处理 HTTP |
| `UserService` | 业务逻辑 |
| `UserMapper` | 访问数据库 |
| `RegisterRequest` | DTO（传数据的小类） |

经典分层（每个服务都这样）：

```
Controller（接请求）
    → Service（业务规则）
        → Mapper/Repository（读写数据库）
```

---

## 练习（可选但推荐）

写一个 `Note` 类，字段：`id`（Long）、`title`（String）、`userId`（Long），带 getter/setter；在 `main` 里 `new` 一个对象，赋值并打印 title。

---

## 本章验收

- [ ] 能解释 class / 对象 / 方法
- [ ] 知道 `List`、`Map` 干什么
- [ ] 知道注解是「标记」，Spring 靠它工作
- [ ] 知道业务代码大概分 Controller / Service / Mapper

完全不用背完整语法书。下一章装环境：[02-env-setup.md](./02-env-setup.md)
