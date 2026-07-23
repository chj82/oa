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
- `admin-action` 只放 Controller，包路径统一使用 `com.oa.action.controller.<业务模块>`；过滤器、拦截器、Web/CORS 配置和全局异常处理统一放在 `admin-boot`。跨模块共享的请求属性名、Cookie 名等常量放在 `admin-common`，禁止 `admin-action` 反向依赖 `admin-boot`。
- 当前登录会话和员工权限缓存仅支持单机 Redis。会话与员工会话索引、权限版本与员工权限键均由 Lua 跨键访问，禁止直接配置 Redis Cluster；如需支持集群，必须先重新设计键槽和批量失效方案。
- 接口鉴权优先读取 Redis 员工权限缓存，缓存命中时不得访问数据库或开启数据库事务。缓存键为 `admin:permission:<employeeId>`，值使用 `EmployeePermissionCache`，TTL 为 1 天；全局版本键为 `admin:permission:version`，不存在时按 `0` 初始化。
- 读取权限缓存必须使用单次 Lua 同时校验全局版本和缓存版本；缓存缺失或版本不一致时，才使用单表权限链重建全部启用接口路径。重建前读取版本，重建期间发生版本递增时允许写入旧版本缓存，使其在下次读取时自动失效。
- 权限相关数据库写入调用 `PermissionService.invalidateAll()` 时，必须立即写入无 TTL 的全局“权限失效处理中”标记；标记存在期间权限缓存读取失败关闭并返回 `503`，不得继续使用旧缓存。数据库提交后使用单次 Lua 原子递增全局版本并按 token 清理标记，明确回滚时只清理当前 token；提交后 Redis 操作失败必须保留标记，后续 `invalidateAll()` 或显式接口同步命令负责递增版本并恢复残留标记，禁止通过 TTL 自动解除。
- 同一员工的权限缓存重建使用单机 Redis 短期锁串行化，锁键为 `admin:permission:rebuild-lock:<employeeId>`，TTL 为 10 秒；持锁方必须二次读取缓存后再查数据库，并使用 token 校验 Lua 释放。未持锁方只允许有限短轮询缓存，不得访问 Mapper，轮询结束仍未命中时返回 `503`。
- Redis 权限缓存 Lua 和版本、锁操作的异常空返回，以及权限缓存 JSON 缺字段、字段类型错误、序列化或反序列化失败，都必须转换为 `AuthenticationInfrastructureException` 并返回 `503`；普通缓存未命中使用明确的 Lua 状态值表达，不得与异常空返回混用。
- 接口同步、员工状态或超级管理员标记、员工角色、角色状态、角色资源、资源状态或父级、资源接口关联、接口状态或路径发生实际变化后，必须调用 `PermissionService.invalidateAll()`，在数据库事务成功提交后通过 Redis `INCR` 原子递增全局版本。任务 5 新增相关写 Service 时必须落实该调用并测试；不得只依赖 1 天 TTL。
- 超级管理员标记写入登录会话；任务 5 修改该标记时，除递增权限版本外还必须调用 `SessionService.invalidateEmployeeSessions()` 使该员工全部旧会话失效。
- Redis 权限缓存不可用时鉴权失败关闭并返回 `503`，不得降级为每请求查数据库，也不得返回 `403` 混淆为业务无权限。
- 系统能力放在各层 `system` 子包；后续业务按相同方式增加业务子包，不新增业务 Maven 模块。
- 默认直接定义具体类；只有存在两个及以上生产实现时才抽取接口，测试 Fake/Mock 不作为拆分接口的理由。Mapper 等框架要求的接口除外。
- 没有对应接口时不使用 `impl` 包；配置类放 `config`，存储适配类按职责放 `store` 等语义化子包。
- Service 不写 SQL，也不构造 MyBatis-Plus `Wrapper`；数据查询和修改封装为语义化 Mapper 方法。简单单表查询优先在 Mapper 默认方法中使用 MyBatis-Plus。数据访问优先拆为单表查询；只有单表查询无法合理完成时才允许关联查询，单条关联查询最多涉及 3 张表，确实需要超过 3 张表时必须先向用户提出单独讨论，未经确认不得实现。
- 非必要不使用 `SELECT ... FOR UPDATE` 等数据库行锁；只有存在明确的并发一致性要求且普通事务、唯一索引或条件更新无法满足时才使用，并说明锁定范围和事务边界。禁止在持有数据库行锁期间调用 Redis、HTTP 等外部服务。
- DTO 放在 `admin-common/model/<业务>/dto`，VO 放在 `admin-common/model/<业务>/vo`，业务枚举统一放在 `admin-common/model/<业务>/enums`，缓存对象等其他模型也必须放在 `admin-common/model/<业务>/<语义子包>`。`admin-service` 等其他模块不得自行建立 `model` 包或定义数据模型。
- 数据对象使用传统 Java Bean：普通 `class`、`private` 字段、公开无参构造，以及显式标准 Getter/Setter；禁止使用 `record`、Lombok。可根据实际用途增加带参构造器或链式 Setter，但不为没有需要的对象添加额外写法。
- Entity、DTO、VO、统一响应、分页对象等所有对象字段上方必须使用 `/** 中文字段说明 */`，明确说明字段用途；Getter/Setter 不写重复的方法注释。
- 自定义类型应避免与 Spring、Swagger 等常用框架类型重名，优先选择语义清晰且不冲突的名称；除确有歧义且无法合理改名外，不在代码中反复使用全限定类名。
- 业务枚举必须定义 `code`、`name`：数据库持久化使用 `code`，中文展示使用 `name`。
- 统一响应和业务异常必须使用 `ExceptionCode` 枚举；`code` 使用全局唯一数字，`name` 使用中文说明，禁止在业务代码、过滤器或拦截器中散落字符串错误码。
- Entity 只映射数据库原始字段类型，不直接使用业务枚举；`TINYINT NOT NULL` 状态使用 `int`，字符串枚举值使用 `String`，枚举转换放在 Service 边界。
- Entity 中数据库 `NOT NULL` 的数值字段使用 Java 基本类型 `long`、`int`，只有允许 `NULL` 的数值字段使用 `Long`、`Integer`。

## 4. 数据库规则

- 表名使用 `t_` 前缀和单数形式，例如 `t_employee`、`t_system_api`。
- 唯一索引使用 `udx_` 前缀，普通索引使用 `idx_` 前缀。
- 不使用数据库外键和级联；关联完整性由 Service 显式维护。
- SQL 仅存放在 `backend/sql/` 并由人工审核执行；应用启动不得自动执行 SQL。
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
- 普通员工缓存有效角色和有效资源关联的全部启用接口路径；超级管理员身份在缓存重建时以数据库 `t_employee.is_superuser` 为准并缓存全部启用接口路径，不信任登录会话中的旧标记，仍不得绕过禁用或未登记接口。
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
