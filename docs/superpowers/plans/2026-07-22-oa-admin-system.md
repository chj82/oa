# OA 基础后台管理系统实施计划

> **执行要求：** 按任务顺序实施，每个行为先写失败测试再写生产代码；用户未要求 Git 提交，因此所有提交步骤省略。

**目标：** 从空仓实现 Java 17 + Spring Boot + MyBatis-Plus + MySQL + Redis 后端，以及 Next.js 管理后台前端。

**架构：** 后端按 `admin-common → admin-entity → admin-dao → admin-service → admin-action → admin-boot` 单向依赖拆分 Maven 模块，包名前缀为 `com.oa`。系统功能在各层 `system` 包中实现；登录使用 Redis 随机会话 Token 和 HttpOnly Cookie；接口权限按员工角色资源关联的接口路径判断。

**技术栈：** Java 17、Spring Boot 3、MyBatis-Plus、Druid、MySQL 8、Spring Data Redis、springdoc-openapi、JUnit 5、Testcontainers、Next.js、TypeScript、Tailwind CSS、TanStack Query、React Hook Form、Zod、Vitest。

---

### 任务 1：工程骨架

**文件：**
- 创建：`pom.xml`、`backend/pom.xml`
- 创建：`backend/admin-{common,entity,dao,service,action,boot}/pom.xml`
- 创建：`backend/admin-boot/src/main/java/com/oa/boot/OaApplication.java`
- 创建：`backend/admin-boot/src/main/resources/application.yml`

- [ ] 用 Maven Enforcer 测试验证 JDK 17 和模块依赖。
- [ ] 创建父 POM 与六个模块 POM。
- [ ] 创建最小启动类和配置。
- [ ] 运行 `mvn test`，预期全部模块构建成功。

### 任务 2：公共契约与数据库模型

**文件：**
- 创建：`backend/admin-common/src/main/java/com/oa/common/**`
- 创建：`backend/admin-entity/src/main/java/com/oa/entity/system/**`
- 创建：`backend/admin-dao/src/main/java/com/oa/dao/system/**`
- 创建：`backend/sql/2026072201-system-init.sql`

- [ ] 编写实体表名、字段映射和状态枚举测试并确认失败。
- [ ] 实现统一响应、分页模型、异常，以及 `model/system/dto`、`model/system/vo`、`model/system/enums` 分包下的 DTO、VO、业务枚举；数据对象使用传统 Java Bean，所有字段添加说明用途的中文注释；枚举使用 `code` 持久化、`name` 中文展示。
- [ ] 实现 8 张表对应 Entity 与 Mapper；所有表使用单一自增 `id` 主键，关联唯一性使用唯一索引；Entity 使用数据库原始字段类型，不直接引用业务枚举，`NOT NULL` 数值字段使用 Java 基本类型。
- [ ] 写入与设计一致的人工执行 DDL。
- [ ] 运行模块测试，确认映射与 SQL 名称一致。

### 任务 3：Redis Cookie 登录

**文件：**
- 创建：`backend/admin-service/src/main/java/com/oa/service/system/AuthService.java`
- 创建：`backend/admin-service/src/main/java/com/oa/service/system/SessionService.java`
- 创建：`backend/admin-boot/src/main/java/com/oa/boot/security/TokenAuthenticationFilter.java`
- 创建：`backend/admin-action/src/main/java/com/oa/action/controller/system/AuthController.java`

- [ ] 测试错误密码不创建会话。
- [ ] 测试成功登录生成 256 位随机 Token，Redis 只保存 Token 哈希。
- [ ] 测试有效请求将空闲过期时间续到 1 天。
- [ ] 测试退出、员工禁用和密码重置使会话失效。
- [ ] 实现 HttpOnly `ADMIN_TOKEN` Cookie，不支持 Authorization 头。
- [ ] 测试公开路径、来源校验和请求上下文清理。

### 任务 4：接口同步与资源鉴权

**文件：**
- 创建：`backend/admin-service/src/main/java/com/oa/service/system/SystemApiService.java`
- 创建：`backend/admin-service/src/main/java/com/oa/service/system/PermissionService.java`
- 创建：`backend/admin-service/src/main/java/com/oa/service/system/store/StringRedisPermissionStore.java`
- 创建：`backend/admin-common/src/main/java/com/oa/common/model/system/cache/EmployeePermissionCache.java`
- 创建：`backend/admin-boot/src/main/java/com/oa/boot/security/ResourceApiAuthorizationInterceptor.java`
- 创建：`backend/admin-boot/src/main/java/com/oa/boot/command/SyncApisRunner.java`
- 修改：`backend/admin-dao/src/main/java/com/oa/dao/system/*Mapper.java`

- [ ] 测试按 Spring MVC 路由模板同步 `name/path/description/status`。
- [ ] 测试重复路径同步失败，移除路径只禁用、不删除关联。
- [ ] 测试单个受保护 Handler 必须恰好声明一个 HTTP Method，未声明或同时声明多个 Method 时同步失败。
- [ ] 测试无资源关联返回 403，多角色权限取并集。
- [ ] 测试超级管理员只绕过资源关联，不绕过禁用接口。
- [ ] 实现路径白名单、登录白名单和资源接口鉴权。
- [ ] 测试权限缓存命中时不访问 Mapper 或开启数据库事务，缓存缺失或版本不一致时按单表链重建。
- [ ] 测试权限缓存使用一天 TTL、单次 Lua 版本校验和原子全局版本递增，Redis 故障返回 503。

### 任务 5：系统管理服务与接口

**文件：**
- 创建：`backend/admin-service/src/main/java/com/oa/service/system/{Employee,Department,Role,Resource,SystemApi}Service.java`
- 创建：`backend/admin-action/src/main/java/com/oa/action/controller/system/**`

- [ ] 按员工、部门、角色、资源、接口顺序分别编写失败测试。
- [ ] 实现分页、详情、新增、修改、启停、删除和关联保存。
- [ ] 实现部门与资源树的环路、层级和删除约束。
- [ ] 实现员工角色、角色资源、资源接口的事务保存。
- [ ] 员工、角色、资源、接口及其关联发生权限相关变化后，调用 `PermissionService.invalidateAll()` 在数据库事务成功提交后递增全局权限版本。
- [ ] 修改员工超级管理员标记时，同时调用 `SessionService.invalidateEmployeeSessions`，避免旧会话继续使用变更前身份。
- [ ] 员工禁用、删除和密码重置必须调用 `SessionService.invalidateEmployeeSessions`，并测试全部登录会话立即失效。
- [ ] 为所有接口补齐 OpenAPI 文档。
- [ ] 运行后端全量测试。

### 任务 6：基础数据初始化

**文件：**
- 创建：`backend/admin-service/src/main/java/com/oa/service/system/SystemInitializationService.java`
- 创建：`backend/admin-boot/src/main/java/com/oa/boot/command/InitializeSystemRunner.java`

- [ ] 测试初始化幂等、事务回滚和初始管理员竞争处理。
- [ ] 实现根部门、系统资源、接口关联和超级管理员初始化。
- [ ] 验证应用启动不自动执行 SQL，初始化只通过显式命令触发。

### 任务 7：Next.js 工程与认证壳

**文件：**
- 创建：`frontend/package.json`、`frontend/src/app/**`
- 创建：`frontend/src/lib/api-client.ts`
- 创建：`frontend/src/features/auth/**`
- 创建：`frontend/src/components/layout/**`

- [ ] 创建 Next.js、TypeScript、Tailwind、Vitest 配置。
- [ ] 测试 API 客户端默认携带 Cookie，401 跳转登录。
- [ ] 实现登录页、后台布局、动态导航和按钮权限组件。
- [ ] 运行前端测试、lint 和构建。

### 任务 8：系统管理页面

**文件：**
- 创建：`frontend/src/features/system/{employees,departments,roles,resources,api-catalog}/**`
- 创建：`frontend/src/app/(admin)/system/**`

- [ ] 实现员工分页、编辑、启停、重置密码和角色分配。
- [ ] 实现部门树维护。
- [ ] 实现角色及资源授权树。
- [ ] 实现资源树与接口关联。
- [ ] 实现接口目录查看、搜索和启停。
- [ ] 覆盖加载、空数据、错误和无权限状态。
- [ ] 运行前端全量测试和构建。

### 任务 9：交付验证

**文件：**
- 创建：`README.md`
- 创建：`.gitignore`

- [ ] 运行 `mvn test` 和 `mvn package`。
- [ ] 运行 `npm test`、`npm run lint` 和 `npm run build`。
- [ ] 核对应用启动不自动执行 SQL。
- [ ] 核对 Swagger、Redis Cookie 和资源接口鉴权配置。
- [ ] 将验证后的文件同步到 `/Users/admin/Documents/workspace/oa`。
- [ ] 检查目标仓库状态，不执行 Git 提交。
