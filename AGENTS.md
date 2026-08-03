# AGENTS.md｜OA 项目协作约定

## 1. 工作目录与沟通

- 唯一项目目录是 `/Users/admin/Documents/workspace/oa`，不得在其他目录开发后再迁移。
- 文档、代码注释和协作回复统一使用中文。
- 用户未明确要求时，不执行 Git commit、push、merge 或其他远端写操作。
- 修改必须聚焦当前任务；不得重构、格式化或删除无关代码和用户已有改动。

## 2. 文档与真相源

- 长期设计以 [Java 后台系统设计](docs/superpowers/specs/2026-07-21-java-admin-system-design.md) 为准。
- 当前实施步骤以 [OA 后台系统实施计划](docs/superpowers/plans/2026-07-22-oa-admin-system.md) 为准。
- 需求纠正形成长期约束时，必须同步更新本文件及对应设计文档，不能只修改代码。
- 代码、测试和文档冲突时，先确认用户最新要求，再统一修正真相源。

## 3. 工程硬规则

- 使用 Java 17、Spring Boot 3、MyBatis-Plus、MySQL、Druid、Redis 和 springdoc-openapi。
- 基础包名统一为 `com.oa`。
- Maven 模块依赖方向固定为：`admin-boot → admin-action → admin-service → admin-dao → admin-entity → admin-common`。
- `admin-action` 只放 Controller，包路径统一使用 `com.oa.action.controller.<业务模块>`；过滤器、拦截器、Web/CORS 配置和全局异常处理统一放在 `admin-boot`。跨模块共享的登录上下文、Cookie 名等稳定能力放在 `admin-common`，禁止 `admin-action` 反向依赖 `admin-boot`。
- 当前登录员工使用普通 `ThreadLocal` 保存请求线程上下文：`TokenAuthenticationFilter` 仅在认证成功后写入，并必须在过滤器 `finally` 中清理；Controller 和权限拦截器可以读取该上下文，Service 继续通过显式参数接收当前员工或员工 ID。禁止使用 `InheritableThreadLocal`，禁止向异步任务或子线程传播登录上下文，禁止继续使用 `HttpServletRequest` 属性传递当前员工。
- 当前登录会话仍使用单机 Redis Lua 原子维护会话键和员工会话索引，不直接支持 Redis Cluster；员工和角色授权缓存不得依赖跨键 Lua，批量读取使用普通 `MGET`。如需将会话迁移到 Redis Cluster，必须先重新设计会话索引键槽和批量失效方案。
- 员工授权关系缓存键为 `admin:employee-auth:<employeeId>`，使用明确缓存对象保存员工状态、超级管理员标记和角色 ID 集合，TTL 固定为 10 分钟；员工状态、超级管理员标记或员工角色发生实际变化后，在数据库事务提交后删除该员工缓存。删除失败允许依靠 TTL 最终收敛，但必须记录错误。
- 角色授权关系缓存键为 `admin:role-auth:<roleId>`，使用明确缓存对象保存角色状态和资源 ID 集合，TTL 固定为 10 分钟；角色状态或角色资源发生实际变化后，在数据库事务提交后删除该角色缓存。删除失败允许依靠 TTL 最终收敛，但必须记录错误。
- 接口鉴权先读取员工授权关系，再使用 `MGET` 批量读取其角色授权关系，最后在 JVM 内根据资源权限快照合并有效资源树和启用接口路径；不得恢复按员工保存完整接口集合与资源树的 `admin:permission:<employeeId>` 缓存，也不得因单个授权变化使全部员工缓存失效。
- 资源权限快照包含资源字段、完整父子关系、资源接口关联以及启用接口路径，使用不可变对象永久驻留 JVM，并通过 `AtomicReference` 整体发布；禁止原地修改已发布快照。超级管理员直接使用快照中的全部有效资源和启用接口，不依赖角色授权。
- 应用启动必须通过 `ApplicationRunner` 在进入可用状态前同步加载完整资源权限快照，并将数据库持久版本单调推进到 Redis 镜像后再次核对版本；初始化成功才允许启动完成，数据库或 Redis 不可用、版本持续不一致或快照加载失败必须导致启动失败，禁止把首次加载推迟到业务请求。
- 资源快照版本使用 `t_system_version` 持久化，版本编码固定为 `permission_snapshot`；Redis 键 `admin:permission:snapshot:version` 仅作为多实例共享镜像且只能单调递增。资源、资源接口关联、接口状态或接口路径等快照内容实际变化时，必须在同一数据库事务内按编码原子递增持久版本，提交后立即同步 Redis。
- 每个实例只使用 `PermissionSnapshotRefreshTask` 每 5 秒读取数据库持久版本并与本地快照版本比较；版本不一致时先推进 Redis 镜像，再只向单线程刷新执行器提交一个后台重建任务。权限请求只读取 JVM 本地快照，不访问 Redis 或数据库检查快照版本。刷新期间当前请求和其他并发请求继续使用启动时已加载的旧快照，不等待刷新，也不因刷新失败返回 `503`；重建必须读取同一数据库一致性视图中的版本和基础数据，发布前再次校验数据库版本，版本变化则放弃结果并由后续定时任务再次触发。若运行期出现本地快照为空的非法状态，必须返回 `503` 并记录错误，不得在请求线程补做首次加载。
- 定时任务统一放在 `com.oa.service.task` 独立包，使用本项目 `Task`、`AbstractTask`、`TaskContext` 和 `TaskManager` 框架；一个定时任务一个具体类，业务 Service 不声明 `@Scheduled`。任务 Bean 名作为手动执行名称，`/api/admin/task/list.htm` 查询任务，`/api/admin/task/run.htm` 异步触发任务；定时和手动触发必须共用 `Task.run()`，`AbstractTask` 负责前置检查、同实例防重入、统一日志和异常处理。
- 员工和角色授权缓存采用 Cache Aside，缓存缺失时按语义化单表查询加载并回填；一次 `MGET` 中多个角色缓存缺失时必须按 ID 集合批量查询并拆分回填，禁止按角色循环访问数据库。允许缓存删除与并发回填造成最长 10 分钟的最终一致窗口。Redis 不可用时鉴权失败关闭并返回 `503`，不得降级为每请求直接查询数据库，也不得返回 `403` 混淆为业务无权限。
- 超级管理员标记写入登录会话；修改该标记时，除删除员工授权关系缓存外，还必须调用 `SessionService.invalidateEmployeeSessions()` 使该员工全部旧会话失效。
- 基础版没有单独的 `built_in` 字段，所有当前 `is_superuser = 1` 的员工均视为受保护超级管理员，禁止降级、删除和禁用；员工禁止删除自己。
- 系统能力放在各层 `system` 子包；后续业务按相同方式增加业务子包，不新增业务 Maven 模块。
- 默认直接定义具体类；只有存在两个及以上生产实现时才抽取接口，测试 Fake/Mock 不作为拆分接口的理由。Mapper 等框架要求的接口除外。
- 没有对应接口时不使用 `impl` 包；配置类放 `config`，存储适配类按职责放 `store` 等语义化子包。
- Service 不写 SQL，也不构造 MyBatis-Plus `Wrapper`；数据查询和修改封装为语义化 Mapper 方法。简单单表查询优先在 Mapper 默认方法中使用 MyBatis-Plus。数据访问优先拆为单表查询；只有单表查询无法合理完成时才允许关联查询，单条关联查询最多涉及 3 张表，确实需要超过 3 张表时必须先向用户提出单独讨论，未经确认不得实现。
- 非必要不使用 `SELECT ... FOR UPDATE` 等数据库行锁；只有存在明确的并发一致性要求且普通事务、唯一索引或条件更新无法满足时才使用，并说明锁定范围和事务边界。禁止在持有数据库行锁期间调用 Redis、HTTP 等外部服务。
- 部门层级写操作是行锁例外：使用 `READ_COMMITTED` 事务，按部门 ID 升序锁定当前部门及目标父级祖先链，锁后必须重载部门图并校验父级、状态、环路和层级；新增子部门与删除父部门通过同一父部门行锁互斥。持锁期间不得调用 Redis、HTTP 等外部服务。
- 项目不使用数据库外键，因此员工删除和员工角色保存必须先锁目标 `t_employee` 行，员工角色保存随后按 ID 升序锁定角色行；角色删除、角色资源保存必须锁定涉及的 `t_role` 行，角色资源保存再按 ID 升序锁定资源行。非根资源新增锁父资源；资源修改按 ID 升序锁当前资源和新父资源；资源状态修改、资源接口保存和资源删除锁目标资源。资源操作不反向锁角色行。多个角色或资源统一按 ID 升序锁定；获取行锁后直到事务结束只执行数据库操作，员工和角色 Redis 缓存只能在事务提交后删除。普通详情、树和分页查询不得加锁。
- 资源新增和移动必须先读取资源图快照，解析目标完整祖先链，再按 ID 升序一次锁定全链；锁后逐项核对父级、状态和类型，任何快照后并发变化都抛出 `RESOURCE_CONCURRENT_MODIFICATION`，不得继续覆盖。资源修改还要核对当前资源的父级、状态和类型。快照内容实际变化时必须在同一数据库事务内递增资源快照持久版本；锁后不得访问 Redis、HTTP 等外部服务。
- 资源修改在锁定当前资源和完整祖先链后，必须再次单表加载最新资源图，所有现有子节点类型、移动环路和整棵子树深度校验都使用锁后资源图，禁止继续使用锁前图判断。
- 资源新增和修改事务显式使用 `READ_COMMITTED`，确保等待父资源或当前资源行锁后，普通二次资源图查询能够看到等待期间已经提交的子节点变化；禁止在 MySQL 默认 `REPEATABLE_READ` 下用普通一致性读冒充锁后最新读。这里依赖父资源和当前资源行锁形成稳定窗口，不扩大为全表锁，也不修改项目全局事务隔离级别。
- 资源接口保存先读取现有关联快照并按集合比较。请求集合相同则不递增资源快照版本，锁资源后再次读取，仍相同直接返回且不删除、不插入；锁后发现并发变化时抛出并发修改异常。请求集合不同时锁资源并全量替换，同时在数据库事务内递增资源快照持久版本。
- 员工角色、角色资源等关联数据批量写入使用 Mapper XML 的单表多 `VALUES` SQL，Service 每批最多 500 条；禁止在循环中逐条执行 `insert`。
- DTO 放在 `admin-common/model/<业务>/dto`，VO 放在 `admin-common/model/<业务>/vo`，业务枚举统一放在 `admin-common/model/<业务>/enums`，缓存对象等其他模型也必须放在 `admin-common/model/<业务>/<语义子包>`。`admin-service` 等其他模块不得自行建立 `model` 包或定义数据模型。
- 数据对象使用传统 Java Bean：普通 `class`、`private` 字段、公开无参构造，以及显式标准 Getter/Setter；禁止使用 `record`、Lombok。可根据实际用途增加带参构造器或链式 Setter，但不为没有需要的对象添加额外写法。
- Entity、DTO、VO、统一响应、分页对象等所有对象字段上方必须使用 `/** 中文字段说明 */`，明确说明字段用途；Getter/Setter 不写重复的方法注释。
- 自定义类型应避免与 Spring、Swagger 等常用框架类型重名，优先选择语义清晰且不冲突的名称；除确有歧义且无法合理改名外，不在代码中反复使用全限定类名。
- 业务枚举必须定义 `code`、`name`：数据库持久化使用 `code`，中文展示使用 `name`。
- 统一响应和业务异常必须使用 `ExceptionCode` 枚举；`code` 使用全局唯一数字，`name` 使用中文说明，禁止在业务代码、过滤器或拦截器中散落字符串错误码。
- 所有对外 JSON 接口响应的最外层必须使用 `ApiResult<T>`，并包含 `success`、`code`、`message`；仅 `ExceptionCode.SUCCESS` 对应 `success = true`，其他响应码一律对应 `success = false`，业务代码不得分别设置造成状态不一致。分页响应使用 `ApiResult<PageResult<T>>`，`PageResult<T>` 只承载分页数据。项目不再为响应对象抽取公共基类。文件下载和流式响应不受此约束。
- Entity 只映射数据库原始字段类型，不直接使用业务枚举；`TINYINT NOT NULL` 状态使用 `int`，字符串枚举值使用 `String`，枚举转换放在 Service 边界。
- Entity 的自增主键 `id` 使用 `Long`，便于表达数据库尚未生成主键；其他数据库 `NOT NULL` 数值字段使用 Java 基本类型 `long`、`int`，只有允许 `NULL` 的数值字段使用 `Long`、`Integer`。

## 4. 数据库规则

- 表名使用 `t_` 前缀和单数形式，例如 `t_employee`、`t_system_api`。
- 唯一索引使用 `udx_` 前缀，普通索引使用 `idx_` 前缀。
- 不使用数据库外键和级联；关联完整性由 Service 显式维护。
- SQL 仅存放在 `backend/sql/` 并由人工审核执行；应用启动不得自动执行 SQL。
- 系统基础数据和接口目录只通过 SQL 初始化，禁止使用 Runner、Controller 扫描或其他运行时代码初始化。新增、删除或修改 Controller 接口路径时，必须同步新增数据变更 SQL，维护 `t_system_api`、`t_system_resource` 和 `t_resource_api`。
- 不引入 Flyway、Liquibase 或其他自动迁移机制。
- 时间字段由应用写入，不依赖数据库默认时间。
- 创建时间、更新时间等日期时间列使用 MySQL `DATETIME(3)`，Entity 使用 `LocalDateTime`；仅日期字段才使用 `DATE` / `LocalDate`，不使用旧 `java.util.Date`。
- 当前未配置 MyBatis-Plus 自动填充器，Service 在新增和修改时显式写入时间字段，Entity 不声明 `FieldFill`。
- 所有表默认使用 `id BIGINT UNSIGNED AUTO_INCREMENT` 单一主键；多列业务唯一性使用 `udx_` 唯一索引表达，只有用户明确要求时才使用联合主键或其他特殊主键。

## 5. 登录与权限规则

- 不使用 JWT 和 Spring Security 框架；仅可使用 `spring-security-crypto` 的 BCrypt 工具。
- 登录 Token 只通过 HttpOnly Cookie `ADMIN_TOKEN` 传输，不支持 `Authorization` 请求头。
- Redis 只保存 Token 哈希对应的会话，空闲有效期 1 天，有效访问自动续期。
- Redis 会话值使用明确的传统 Java 缓存对象，不使用 `Map` 表达缓存字段；缓存对象不得直接作为接口 VO 返回。
- 权限链路固定为：员工 → 角色 → 资源 → 接口。
- 普通员工通过员工授权关系缓存、角色授权关系缓存和本地资源权限快照实时合成有效资源与全部启用接口路径；超级管理员标记以员工授权关系缓存为准并使用快照中的全部有效资源和启用接口，仍不得绕过禁用或未登记接口。
- 接口路径就是权限资源标识，不定义独立资源码，也不按 HTTP Method 区分。
- 不同 HTTP Method 不得复用相同接口路径。
- `t_system_api` 的业务字段只有 `name`、`path`、`description`、`status`，不得增加 `method` 或 `code`。
- Controller 不定义权限注解；除固定白名单外，接口默认根据资源与接口关联鉴权。

## 6. 开发与验证

- 行为代码遵循测试驱动：先写失败测试并确认失败原因，再写最小实现，最后运行相关测试和全量回归。
- Java 代码格式化使用 Google Java Format 1.28.0，工具固定为 `tools/google-java-format-1.28.0-all-deps.jar`。
- 修改任何 Java 文件后，必须自动对所有本次修改的 `.java` 文件执行 `java -jar tools/google-java-format-1.28.0-all-deps.jar --replace <文件...>`，再运行相关测试。
- 格式化必须使用 `--replace`，范围只包含本次修改的 Java 文件，不得顺带格式化无关文件。
- 配置、注释等非运行行为可以直接修改，但必须通过静态检查、源码扫描或人工核对验证。
- 后端最小验证：`mvn test`。
- 后端交付验证：`mvn test` 和 `mvn package`。
- 前端交付验证：`npm test`、`npm run lint` 和 `npm run build`。
- 未执行真实 MySQL、Redis 或浏览器联调时，必须明确说明，不能声称对应验收已完成。

## 7. 反复踩坑

- 不要把 DTO、VO 平铺在业务模型包中，必须分别进入 `dto`、`vo` 子包。
- 不要使用 `record` 代替传统 Java Bean。
- 不要省略对象字段中文注释。
- 不要让枚举名称承担数据库值；持久化与展示分别使用 `code`、`name`。
- 不要把代码写入 Codex 临时目录；始终直接修改指定项目目录。
