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

- [x] 用 Maven Enforcer 测试验证 JDK 17 和模块依赖。
- [x] 创建父 POM 与六个模块 POM。
- [x] 创建最小启动类和配置。
- [x] 运行 `mvn test`，预期全部模块构建成功。

### 任务 2：公共契约与数据库模型

**文件：**
- 创建：`backend/admin-common/src/main/java/com/oa/common/**`
- 创建：`backend/admin-entity/src/main/java/com/oa/entity/system/**`
- 创建：`backend/admin-dao/src/main/java/com/oa/dao/system/**`
- 创建：`backend/sql/2026072201-system-init.sql`

- [x] 编写实体表名、字段映射和状态枚举测试并确认失败。
- [x] 实现统一响应、分页模型、异常，以及 `model/system/dto`、`model/system/vo`、`model/system/enums` 分包下的 DTO、VO、业务枚举；数据对象使用传统 Java Bean，所有字段添加说明用途的中文注释；枚举使用 `code` 持久化、`name` 中文展示。
- [x] 实现 8 张表对应 Entity 与 Mapper；所有表使用单一自增 `id` 主键，关联唯一性使用唯一索引；Entity 使用数据库原始字段类型，不直接引用业务枚举，`NOT NULL` 数值字段使用 Java 基本类型。
- [x] 写入与设计一致的人工执行 DDL。
- [x] 运行模块测试，确认映射与 SQL 名称一致。

### 任务 3：Redis Cookie 登录

**文件：**
- 创建：`backend/admin-service/src/main/java/com/oa/service/system/AuthService.java`
- 创建：`backend/admin-service/src/main/java/com/oa/service/system/SessionService.java`
- 创建：`backend/admin-boot/src/main/java/com/oa/boot/security/TokenAuthenticationFilter.java`
- 创建：`backend/admin-action/src/main/java/com/oa/action/controller/system/AuthController.java`

- [x] 测试错误密码不创建会话。
- [x] 测试成功登录生成 256 位随机 Token，Redis 只保存 Token 哈希。
- [x] 测试有效请求将空闲过期时间续到 1 天。
- [x] 测试退出、员工禁用和密码重置使会话失效。
- [x] 实现 HttpOnly `ADMIN_TOKEN` Cookie，不支持 Authorization 头。
- [x] 测试公开路径、来源校验和请求上下文清理。

### 任务 4：接口同步与资源鉴权

**文件：**
- 创建：`backend/admin-service/src/main/java/com/oa/service/system/SystemApiService.java`
- 创建：`backend/admin-service/src/main/java/com/oa/service/system/PermissionService.java`
- 创建：`backend/admin-service/src/main/java/com/oa/service/system/store/StringRedisPermissionStore.java`
- 创建：`backend/admin-common/src/main/java/com/oa/common/model/system/cache/EmployeePermissionCache.java`
- 创建：`backend/admin-boot/src/main/java/com/oa/boot/security/ResourceApiAuthorizationInterceptor.java`
- 创建：`backend/admin-boot/src/main/java/com/oa/boot/command/SyncApisRunner.java`
- 修改：`backend/admin-dao/src/main/java/com/oa/dao/system/*Mapper.java`

- [x] 测试按 Spring MVC 路由模板同步 `name/path/description/status`。
- [x] 测试重复路径同步失败，移除路径只禁用、不删除关联。
- [x] 测试单个受保护 Handler 必须恰好声明一个 HTTP Method，未声明或同时声明多个 Method 时同步失败。
- [x] 测试无资源关联返回 403，多角色权限取并集。
- [x] 测试超级管理员只绕过资源关联，不绕过禁用接口。
- [x] 实现路径白名单、登录白名单和资源接口鉴权。
- [x] 测试权限缓存命中时不访问 Mapper 或开启数据库事务，缓存缺失或版本不一致时按单表链重建。
- [x] 测试权限缓存使用一天 TTL、单次 Lua 版本校验和原子全局版本递增，Redis 故障返回 503。

### 任务 5：系统管理服务与接口

员工管理子任务已完成：员工分页、详情、新增、修改、启停、删除、密码重置和角色保存已按单表查询与事务边界实现。部门管理子任务已完成：部门树、详情、新增、修改、启停、删除及层级约束已按单表查询实现。角色管理子任务已完成：角色分页、详情、新增、修改、启停、删除和资源保存已按单表查询与事务边界实现。系统资源管理子任务已完成代码实现：包含资源树、详情、新增、修改、启停、删除、资源接口回显和全量保存，并实现类型矩阵、64 层边界、唯一索引异常转换、关联删除约束及权限缓存统一失效。系统接口管理子任务已完成代码实现：只提供接口目录分页、详情和启停，不提供新增、修改或删除；接口同步已区分权限变化与纯元数据变化，并通过条件更新保护并发人工状态。任务 5 已通过整体需求审查、代码质量审查和后端全量测试。

系统资源删除和资源接口保存按“权限失效标记 → 锁定目标资源行 → 仅执行数据库校验与写入”的顺序处理无外键并发完整性，资源接口关联每批最多写入 500 条。资源新增和修改锁定完整祖先链，资源修改在锁后重新加载最新资源图校验子节点类型、环路和子树深度。资源树通过一次单表查询构建，发现孤儿、环路或超过 64 层时失败；未执行真实 MySQL 并发事务验证，留待数据库联调阶段完成。

资源接口保存只接受当时已启用的接口；接口后续禁用时保留已有关联并由鉴权忽略，不锁定系统接口行。

角色管理已通过单元测试、Web 参数校验测试及 DDL/Mapper 结构门禁；尚未连接真实 MySQL 验证唯一索引冲突和分页 SQL，后续数据库联调时补充，不使用 H2 模拟替代。

角色删除、角色资源保存和员工角色保存已按“权限失效标记 → 员工行锁（员工角色保存）→ 角色行升序锁 → 关联读写”的顺序处理无外键并发完整性；员工删除同样在权限失效后锁定员工行再删除关联。关联插入使用每批最多 500 条的多 `VALUES` SQL。真实 MySQL 并发事务仍需在数据库联调阶段验证。

**文件：**
- 创建：`backend/admin-service/src/main/java/com/oa/service/system/{Employee,Department,Role,Resource,SystemApi}Service.java`
- 创建：`backend/admin-action/src/main/java/com/oa/action/controller/system/**`

- [x] 按员工、部门、角色、资源、接口顺序分别编写失败测试。
- [x] 实现分页、详情、新增、修改、启停、删除和关联保存。
- [x] 实现部门与资源树的环路、层级和删除约束。
- [x] 实现员工角色、角色资源、资源接口的事务保存。
- [x] 员工、角色、资源、接口及其关联发生权限相关变化后，调用 `PermissionService.invalidateAll()` 在数据库事务成功提交后递增全局权限版本。
- [x] 修改员工超级管理员标记时，同时调用 `SessionService.invalidateEmployeeSessions`，避免旧会话继续使用变更前身份。
- [x] 员工禁用、删除和密码重置必须调用 `SessionService.invalidateEmployeeSessions`，并测试全部登录会话立即失效。
- [x] 为所有接口补齐 OpenAPI 文档。
- [x] 运行后端全量测试。

### 任务 6：基础数据初始化

已完成显式命令初始化：单事务幂等补齐总部、系统管理资源、34 条资源接口关联和初始超级管理员；初始化唯一索引竞争完整回滚后最多重试一次，普通启动不执行初始化或 SQL。任务 6 已通过需求审查、代码质量审查和后端全量测试。

**文件：**
- 创建：`backend/admin-service/src/main/java/com/oa/service/system/SystemInitializationService.java`
- 创建：`backend/admin-boot/src/main/java/com/oa/boot/command/InitializeSystemRunner.java`

- [x] 测试初始化幂等、事务回滚和初始管理员竞争处理。
- [x] 实现根部门、系统资源、接口关联和超级管理员初始化。
- [x] 验证应用启动不自动执行 SQL，初始化只通过显式命令触发。

### 任务 7：Next.js 工程与认证壳

**文件：**
- 创建：`frontend/package.json`、`frontend/src/app/**`
- 创建：`frontend/src/lib/api-client.ts`
- 创建：`frontend/src/features/auth/**`
- 创建：`frontend/src/components/layout/**`

- [x] 创建 Next.js、TypeScript、Tailwind、Vitest 配置。
- [x] 测试 API 客户端默认携带 Cookie，401 跳转登录。
- [x] 实现登录页、后台布局、动态导航和按钮权限组件。
- [x] 运行前端测试、lint 和构建。

### 任务 8：系统管理页面

**文件：**
- 创建：`frontend/src/features/system/{employees,departments,roles,resources,api-catalog}/**`
- 创建：`frontend/src/app/(admin)/system/**`

- [x] 实现员工分页、编辑、启停、重置密码和角色分配。
- [x] 实现部门树维护。
- [x] 实现角色及资源授权树。
- [x] 实现资源树与接口关联。
- [x] 实现接口目录查看、搜索和启停。
- [x] 覆盖加载、空数据、错误和无权限状态。
- [x] 运行前端全量测试和构建。

### 任务 9：交付验证

**文件：**
- 创建：`README.md`
- 创建：`.gitignore`

- [x] 运行 `mvn test` 和 `mvn package`。
- [x] 运行 `npm test`、`npm run lint` 和 `npm run build`。
- [x] 核对应用启动不自动执行 SQL。
- [x] 核对 Swagger、Redis Cookie 和资源接口鉴权配置。
- [x] 将验证后的文件同步到 `/Users/admin/Documents/workspace/oa`。
- [x] 检查目标仓库状态，不执行 Git 提交。
