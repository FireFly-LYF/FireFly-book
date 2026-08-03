# 03 · 第一个 Spring Boot 项目

## 本章目标

- 用 Spring Initializr 创建工程
- 写一个 `/hello` 接口
- 用浏览器或 curl 访问成功

预计：半天～1 天。

---

## 步骤 1：创建项目（推荐：网站生成 + Cursor 打开）

> 你用的是 **Cursor**（和 VS Code 同类）：本身没有 IDEA 那种「一键 New Spring 项目」向导。  
> **最省事做法**：用官网生成 zip → 解压进仓库 → 用 Cursor 打开。

### 方式 A：start.spring.io（推荐，配合 Cursor）

1. 浏览器打开 https://start.spring.io/
2. 填写：

| 项 | 值 |
|----|-----|
| Project | Maven |
| Language | Java |
| Spring Boot | 4.1.x 或 4.0.x（选当前稳定版） |
| Group | `com.firefly` |
| Artifact | `hello-service` |
| Name | `hello-service` |
| Packaging | Jar |
| Java | 17 |

3. Dependencies 点 **Add**，搜索并加入：
   - **Spring Web**
4. 点 **GENERATE** 下载 zip。
5. 解压到（若已有空目录可先删掉再解压，避免混在一起）：

```
FireFly-book/services/hello-service/
```

解压后该目录下应直接能看到 `pom.xml`（不要多套一层多余文件夹）。

### 方式 B：命令行生成（也适合 Cursor）

若已安装 [Spring Boot CLI](https://docs.spring.io/spring-boot/docs/current/reference/html/cli.html) 可跳过；没有的话继续用方式 A 即可。

也可在 PowerShell 用 curl 直接拉（无需打开网页）：

```powershell
cd d:\A_Software\Java\SAVE\FireFly-book\services
# PowerShell 下请用 curl.exe，避免被别名成 Invoke-WebRequest
curl.exe -o hello.zip "https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=4.1.0&baseDir=hello-service&groupId=com.firefly&artifactId=hello-service&name=hello-service&packageName=com.firefly.helloservice&javaVersion=17&dependencies=web"
Expand-Archive -Path hello.zip -DestinationPath . -Force
Remove-Item hello.zip
```

（`bootVersion` 须为 [start.spring.io](https://start.spring.io) 当前仍提供的版本。若生成的 `pom.xml` 里是 `4.1.0.RELEASE`，请改成 Maven Central 上的 **`4.1.0`**，否则父 POM 解析会报错。）

### 方式 C：IntelliJ IDEA

若你另装了 IDEA：`File → New → Project → Spring Initializr`，选项同方式 A。用 Cursor 的可忽略本方式。

---

## 步骤 2：用 Cursor 打开并装扩展

1. 在 Cursor 中：`File → Open Folder`，打开带 `pom.xml` 的 `hello-service` 目录  
   （也可以继续打开整个 `FireFly-book`，在左侧进到 `services/hello-service`）。
2. 安装扩展（扩展市场搜名字安装）：
   - **Extension Pack for Java**（Microsoft，会带上 Language Support / Debugger / Maven 等）
   - 可选：**Spring Boot Extension Pack**（方便看运行配置、application 提示）
3. 打开后右下角会提示加载 Java 项目；等 Maven 依赖下载完（状态栏有进度）。
4. 找到启动类，类似：

```
src/main/java/com/firefly/helloservice/HelloServiceApplication.java
```

里面有 `@SpringBootApplication` 和 `main` 方法。

若 Cursor 没认出 Maven 项目：命令面板 `Ctrl+Shift+P` → 输入 `Java: Clean Java Language Server Workspace` → 重启后再等索引。
---

## 步骤 3：改端口（避免和网关 8080 冲突）

编辑 `src/main/resources/application.properties`（或 `application.yml`）：

**properties：**

```properties
server.port=8081
```

**或 yml：**

```yaml
server:
  port: 8081
```

---

## 步骤 4：写第一个接口

在 `com.firefly.helloservice` 包下新建 `HelloController.java`：

```java
package com.firefly.helloservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "hello firefly";
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
```

说明：

- `@RestController`：这个类负责处理 HTTP，返回值直接作为响应体
- `@GetMapping("/hello")`：GET `http://主机:端口/hello`

---

## 步骤 5：启动（Cursor）

任选一种：

### 5A：编辑器里点运行

1. 打开 `HelloServiceApplication.java`
2. `main` 方法上方会出现 **Run | Debug**，点 **Run**
3. 终端出现 `Started HelloServiceApplication` 即成功

### 5B：终端用 Maven（最稳）

在 `hello-service` 目录下：

```powershell
cd d:\A_Software\Java\SAVE\FireFly-book\services\hello-service
mvn spring-boot:run
```

验证：

```powershell
curl http://127.0.0.1:8081/hello
```

浏览器打开：http://127.0.0.1:8081/hello → 应看到 `hello firefly`  
再访问：http://127.0.0.1:8081/health → `OK`

停止：在运行该进程的终端里 `Ctrl+C`。
---

## 步骤 6：看懂项目结构

```
hello-service/
  pom.xml                          ← 依赖与构建配置
  src/main/java/.../Application    ← 启动入口
  src/main/java/.../Controller     ← 接口
  src/main/resources/
    application.properties         ← 端口、数据库等配置
  src/test/                        ← 测试（可先忽略）
```

`pom.xml` 里 `<dependency>` 就是「引入的库」。现在你有 `spring-boot-starter-web`，所以才能写 Controller。

---

## 步骤 7：改一改加深印象

把 `/hello` 改成返回一句带名字的话：

```java
@GetMapping("/hello")
public String hello() {
    return "hello, 我是第一个 Java 服务";
}
```

**保存后需要重启应用**（或开 Spring DevTools 热更新；新手重启最稳）。

---

## 常见问题

| 现象 | 处理 |
|------|------|
| 端口被占用 | 换 `server.port`，或关掉占用 8081 的进程 |
| 依赖下载很慢 | 配置 Maven 阿里云镜像（见第 02 章） |
| 404 | 路径是否写对；是否启动成功；端口是否对 |
| 找不到符号 @RestController | `pom.xml` 是否加了 Spring Web；等 Java 扩展索引完；`Ctrl+Shift+P` → Maven 刷新 |
| Cursor 没有 Run 按钮 | 确认已装 Extension Pack for Java；打开的是含 `pom.xml` 的工程 |
| `mvn` 不是内部命令 | JDK/Maven 已装但未进 Path，或终端没重开（见第 02 章） |

---

## 本章验收

- [ ] 项目能启动
- [ ] `/hello` 与 `/health` 都能访问
- [ ] 知道 `pom.xml`、启动类、Controller、`application` 配置各干什么

下一章：正经写 JSON API → [04-rest-api.md](./04-rest-api.md)
