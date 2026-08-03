# 02 · 开发环境安装

## 本章目标

装好并能验证：

1. JDK 17
2. Maven
3. Cursor / VS Code（装 Java 扩展）或 IDEA

4. MySQL 8
5. Redis（后面用，可先装）
6. 会用 Apifox / Postman / curl 测接口

---

## 步骤 1：安装 JDK 17

### Windows 推荐

1. 打开 [Adoptium Temurin 17](https://adoptium.net/) 下载 Windows x64 JDK 17。
2. 安装到例如 `C:\Program Files\Eclipse Adoptium\jdk-17.x.x`
3. 配置环境变量：
   - 新建 `JAVA_HOME` = JDK 安装目录
   - `Path` 里加入 `%JAVA_HOME%\bin`

### 验证

打开 **新的** PowerShell：

```powershell
java -version
javac -version
```

应看到类似 `openjdk version "17.x.x"`。  
若仍是旧版本，多半是 Path 里还有别的 Java，把 JDK17 的 bin 挪到更前面。

---

## 步骤 2：安装 Maven

1. 下载 [Maven](https://maven.apache.org/download.cgi) 二进制 zip。
2. 解压到例如 `D:\Tools\apache-maven-3.9.x`
3. 环境变量：
   - `MAVEN_HOME` = 解压目录
   - Path 增加 `%MAVEN_HOME%\bin`

验证：

```powershell
mvn -v
```

应同时显示 Maven 版本和 Java 版本。

### 可选：换国内镜像（下载依赖更快）

编辑 `%USERPROFILE%\.m2\settings.xml`（没有就新建），加入阿里云镜像，例如：

```xml
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>*</mirrorOf>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
```

---

## 步骤 3：编辑器（用 Cursor 即可，不必装 IDEA）

本教程默认你用 **Cursor**（和 VS Code 同类）：

1. 确认本机已装好 **JDK 17**、**Maven**（见上文），终端里 `java -version`、`mvn -v` 正常。
2. 在 Cursor 扩展市场安装：
   - **Extension Pack for Java**（Microsoft）
   - 可选：**Spring Boot Extension Pack**
3. 打开工程：`File → Open Folder`，选带 `pom.xml` 的服务目录（或打开整个 `FireFly-book`）。
4. 运行方式：
   - 打开启动类，点 `main` 上方的 **Run**；或
   - 终端执行：`mvn spring-boot:run`

若你更习惯 IntelliJ IDEA Community，也可以装，操作等价：打开含 `pom.xml` 的文件夹即可。**不必两个都装。**
---

## 步骤 4：安装 MySQL 8

任选一种：

### 方式 A：安装版

从官网装 MySQL 8，记住 root 密码（教程示例常用 `123456`，**仅本地开发**）。

### 方式 B：Docker（若你已会）

```powershell
docker run -d --name ff-mysql -e MYSQL_ROOT_PASSWORD=123456 -p 3306:3306 mysql:8.0
```

### 验证

用命令行或 [MySQL Workbench](https://dev.mysql.com/downloads/workbench/) / Navicat / DBeaver 连接：

- host: `127.0.0.1`
- port: `3306`
- user: `root`
- password: 你设的密码

执行：

```sql
CREATE DATABASE IF NOT EXISTS user DEFAULT CHARACTER SET utf8mb4;
SHOW DATABASES;
```

能看到 `user` 即成功。

---

## 步骤 5：安装 Redis（可本周稍后）

```powershell
docker run -d --name ff-redis -p 6379:6379 redis:7
```

或 Windows 下使用 Memurai / 官方支持的安装方式。

验证：

```powershell
docker exec -it ff-redis redis-cli ping
```

应返回 `PONG`。  
你的 Gateway 也用 Redis，可共用同一个本地实例。

---

## 步骤 6：接口调试工具

任选其一：

- [Apifox](https://apifox.com/)（中文友好）
- Postman
- 命令行 curl

示例：

```powershell
curl http://127.0.0.1:8081/hello
```

---

## 步骤 7：建议的本地端口规划（避免冲突）

| 进程 | 端口 |
|------|------|
| Gateway | 8080 |
| user-service | 9001 |
| content-service | 9002 |
| media-service | 9003 |
| social-service | 9004 |
| notify-service | 9005 |
| feed-service | 9006 |
| search-service | 9007 |
| MySQL | 3306 |
| Redis | 6379 |

练手用的 hello 项目可用 **8081**。

---

## 步骤 8：代码放哪

建议在仓库里建：

```
d:\A_Software\Java\SAVE\FireFly-book\services\
```

教程文档在：

```
d:\A_Software\Java\SAVE\FireFly-book\Java-Tutorial\
```

**先不要改 Gateway 代码**，Java 服务独立启动。

---

## 本章验收

在 PowerShell 中下列命令都能正常输出版本或结果：

```powershell
java -version
mvn -v
```

并且能连上 MySQL，已创建数据库 `user`。

---

下一章：用 Spring Boot 跑起来 → [03-spring-boot-hello.md](./03-spring-boot-hello.md)
