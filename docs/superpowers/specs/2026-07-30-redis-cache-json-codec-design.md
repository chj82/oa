# Redis 缓存 JSON Codec 设计

## 1. 目标

Redis 登录会话和员工权限缓存继续使用字符串存储，通过统一的 `RedisCacheJsonCodec` 封装 Jackson 配置和 JSON 编解码，修复权限资源树包含 `LocalDateTime` 时无法序列化并返回 `503` 的问题。

## 2. 序列化边界

- `RedisCacheJsonCodec` 是 `admin-service` 内的 Spring Bean，负责 Java 对象与 JSON 字符串互转。
- Codec 基于 Spring Boot 已配置的 `ObjectMapper.copy()` 创建专用实例，并显式注册 `JavaTimeModule`、关闭日期时间戳数组输出。
- `StringRedisSessionStore` 使用 Codec 转换 `LoginSessionCache`。
- `StringRedisPermissionStore` 使用 Codec 转换 `EmployeePermissionCache`。
- Redis 键、TTL、Lua 脚本、JSON 字段名和缓存模型保持不变。
- 不启用自动类型能力，不在 JSON 中写入 Java 类型信息，不使用 JDK 对象序列化。
- 不在 `admin-common` 增加静态 JSON 工具类，避免基础模块依赖 Jackson 和形成全局隐式配置。

## 3. 数据校验与异常

- Codec 将 Jackson 编解码异常统一包装为自身的 JSON 转换异常，不绑定业务异常码。
- 登录会话 JSON 无法解析或转换时，Store 继续抛出 `IllegalStateException`。
- 权限缓存先通过 Codec 解析为 `JsonNode`，严格校验根节点、非负整数版本、接口路径数组和资源数组，再转换为 `EmployeePermissionCache`。
- 权限缓存解析、序列化或类型转换失败时，统一转换为 `AuthenticationInfrastructureException`，接口返回 `503`。
- 普通缓存未命中仍使用 Lua 明确状态表达，不与格式异常混用。

## 4. 依赖与配置

- 保留 `admin-service` 对 `jackson-databind` 的依赖，不新增 Fastjson2。
- 删除只返回裸 `new ObjectMapper()` 的 `loginSessionObjectMapper` Bean，由 Codec 复用 Spring Boot 全局 `ObjectMapper` 的基础配置并隔离 Redis 专用配置。

## 5. 验证

- 登录会话 JSON 字符串写入和读取测试通过。
- 权限缓存包含多层资源树、枚举和 `LocalDateTime` 时能够完整往返。
- 缺字段、字段类型错误、非法 JSON 和序列化异常仍按原契约失败关闭。
- 运行 Google Java Format、`mvn test` 和 `mvn package`。
- 实际清理旧权限缓存并重新访问 `/api/auth/current`，确认生成字符串缓存且接口不再返回 `503`。
