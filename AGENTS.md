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
- 当前登录会话仅支持单机 Redis。会话与员工会话索引由同一 Lua 脚本原子维护，禁止直接配置 Redis Cluster；如需支持集群，必须先重新设计键槽和批量失效方案。
- 系统能力放在各层 `system` 子包；后续业务按相同方式增加业务子包，不新增业务 Maven 模块。
- 默认直接定义具体类；只有存在两个及以上生产实现时才抽取接口，测试 Fake/Mock 不作为拆分接口的理由。Mapper 等框架要求的接口除外。
- 没有对应接口时不使用 `impl` 包；配置类放 `config`，存储适配类按职责放 `store` 等语义化子包。
- Service 不写 SQL，也不构造 MyBatis-Plus `Wrapper`；数据查询和修改封装为语义化 Mapper 方法。简单单表查询优先在 Mapper 默认方法中使用 MyBatis-Plus，复杂查询和联表查询放 Mapper XML。
- 非必要不使用 `SELECT ... FOR UPDATE` 等数据库行锁；只有存在明确的并发一致性要求且普通事务、唯一索引或条件更新无法满足时才使用，并说明锁定范围和事务边界。禁止在持有数据库行锁期间调用 Redis、HTTP 等外部服务。
- DTO 放在 `admin-common/model/<业务>/dto`，VO 放在 `admin-common/model/<业务>/vo`，业务枚举统一放在 `admin-common/model/<业务>/enums`。
- 数据对象使用传统 Java Bean：普通 `class`、`private` 字段、无参构造，以及显式标准 Getter/Setter；禁止使用 `record`、Lombok、链式 Setter 或仅构造器赋值的对象。
- Entity、DTO、VO、统一响应、分页对象等所有对象字段上方必须使用 `/** 中文字段说明 */`，明确说明字段用途；Getter/Setter 不写重复的方法注释。
- 业务枚举必须定义 `code`、`name`：数据库持久化使用 `code`，中文展示使用 `name`。
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
