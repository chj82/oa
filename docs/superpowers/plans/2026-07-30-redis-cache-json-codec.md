# Redis 缓存 JSON Codec 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 统一封装 Redis 缓存 JSON 编解码，修复权限资源树包含 `LocalDateTime` 时序列化失败并返回 `503` 的问题。

**架构：** 在 `admin-service` 的存储包新增 Spring Bean `RedisCacheJsonCodec`，基于 Spring Boot 管理的 `ObjectMapper.copy()` 注册 Java 时间模块并使用字符串 JSON。登录会话和权限 Store 只依赖该 Codec，权限 Store 保留严格字段校验和现有失败关闭语义。

**技术栈：** Java 17、Spring Boot 3、Jackson、Spring Data Redis、JUnit 5、Mockito

---

### 任务 1：用测试锁定 Codec 的时间类型往返行为

**文件：**
- 新建：`backend/admin-service/src/test/java/com/oa/service/system/store/RedisCacheJsonCodecTest.java`
- 新建：`backend/admin-service/src/main/java/com/oa/service/system/store/RedisCacheJsonCodec.java`
- 新建：`backend/admin-service/src/main/java/com/oa/service/system/store/RedisCacheJsonException.java`
- 修改：`backend/admin-service/pom.xml`

- [ ] **步骤 1：编写失败测试**

新增测试，使用 Spring Boot 配置的 `ObjectMapper` 构造 Codec，验证包含多层 `ResourceVO`、`ResourceType`、`SystemStatus` 和 `LocalDateTime` 的 `EmployeePermissionCache` 能写成字符串并完整读回；同时验证非法 JSON 被包装为 `RedisCacheJsonException`。

- [ ] **步骤 2：运行测试并确认按预期失败**

运行：`cd backend && mvn -pl admin-service -am -Dtest=RedisCacheJsonCodecTest -Dsurefire.failIfNoSpecifiedTests=false test`

预期：因 `RedisCacheJsonCodec` 尚不存在而编译失败。

- [ ] **步骤 3：实现最小 Codec**

为 `admin-service` 声明由 Spring Boot 管理版本的 `jackson-datatype-jsr310` 依赖。实现 `RedisCacheJsonCodec`：构造器接收 Spring `ObjectMapper`，复制后注册 `JavaTimeModule` 并关闭 `WRITE_DATES_AS_TIMESTAMPS`；提供 `write`、`read`、`readTree`、`treeToValue` 四个方法，统一把 Jackson 或运行时转换异常包装为 `RedisCacheJsonException`。

- [ ] **步骤 4：运行测试并确认通过**

运行：`cd backend && mvn -pl admin-service -am -Dtest=RedisCacheJsonCodecTest -Dsurefire.failIfNoSpecifiedTests=false test`

预期：Codec 测试全部通过。

### 任务 2：两个 Redis Store 统一使用 Codec

**文件：**
- 修改：`backend/admin-service/src/main/java/com/oa/service/system/store/StringRedisSessionStore.java`
- 修改：`backend/admin-service/src/main/java/com/oa/service/system/store/StringRedisPermissionStore.java`
- 修改：`backend/admin-service/src/test/java/com/oa/service/system/store/StringRedisSessionStoreTest.java`
- 修改：`backend/admin-service/src/test/java/com/oa/service/system/store/StringRedisPermissionStoreTest.java`

- [ ] **步骤 1：改造测试并确认失败**

将两个 Store 测试改为注入真实或 Mock 的 `RedisCacheJsonCodec`；新增权限缓存完整往返测试，断言多层资源树和时间字段写入 Redis 字符串后可读回；保留非法 JSON、缺字段、字段类型错误和序列化异常测试。

运行：`cd backend && mvn -pl admin-service -am -Dtest=StringRedisSessionStoreTest,StringRedisPermissionStoreTest -Dsurefire.failIfNoSpecifiedTests=false test`

预期：Store 构造器尚未接收 Codec，编译失败。

- [ ] **步骤 2：最小改造 Store**

把两个 Store 的 `ObjectMapper` 依赖替换为 `RedisCacheJsonCodec`。会话 Codec 异常转换为 `IllegalStateException`；权限 Codec 异常转换为 `AuthenticationInfrastructureException`，并继续先 `readTree` 校验字段，再 `treeToValue` 转换对象。

- [ ] **步骤 3：运行相关测试并确认通过**

运行：`cd backend && mvn -pl admin-service -am -Dtest=RedisCacheJsonCodecTest,StringRedisSessionStoreTest,StringRedisPermissionStoreTest -Dsurefire.failIfNoSpecifiedTests=false test`

预期：相关测试全部通过。

### 任务 3：收敛 Spring 装配并完成交付验证

**文件：**
- 删除：`backend/admin-service/src/main/java/com/oa/service/system/config/RedisSessionConfiguration.java`
- 修改：`backend/admin-boot/src/test/java/com/oa/boot/config/AuthenticationAssemblyTest.java`

- [ ] **步骤 1：修改装配测试并确认失败**

装配测试改为提供 Spring Boot `ObjectMapper`，导入 `RedisCacheJsonCodec`，断言 Codec 和两个会话组件均能无歧义装配，不再断言 `loginSessionObjectMapper` Bean。

运行：`cd backend && mvn -pl admin-boot -am -Dtest=AuthenticationAssemblyTest -Dsurefire.failIfNoSpecifiedTests=false test`

预期：旧配置仍存在或新 Codec 尚未完整接入时测试失败。

- [ ] **步骤 2：删除裸 ObjectMapper 配置**

删除 `RedisSessionConfiguration`，确保生产装配只使用 Spring Boot `ObjectMapper` 和专用 Codec 副本。

- [ ] **步骤 3：格式化本次 Java 文件**

运行：`java -jar tools/google-java-format-1.28.0-all-deps.jar --replace <本次新增和修改的 Java 文件>`

预期：命令退出码为 0，且不格式化无关 Java 文件。

- [ ] **步骤 4：运行后端交付验证**

运行：`cd backend && mvn test`

预期：全部测试通过。

运行：`cd backend && mvn package`

预期：打包成功。

运行：`git diff --check && git diff --cached --check`

预期：无空白错误；保留用户原有暂存状态，不提交 Git。
