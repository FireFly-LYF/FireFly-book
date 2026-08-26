# firefly-ai-common

FireFly Python AI 公共库：配置、MQ 常量、事件模型、HMAC、HTTP/Redis 客户端基座。

对标 Java 的 `services/firefly-internal-auth`。

业务逻辑（审核规则、标签提取、排序算法）放在各 `*-service`，不放在本包。
