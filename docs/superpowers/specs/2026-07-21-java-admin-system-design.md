# Java 基础后台管理系统设计

## 1. 建设目标

从零搭建一个前后端分离的基础后台管理系统，为后续业务功能提供员工、部门、角色、资源和接口授权能力。

基础功能包括：

- 用户名和密码登录
- 员工管理
- 部门管理
- 角色管理
- 资源管理，包括目录、菜单和按钮
- 接口目录管理
- 角色资源授权
- 基于当前员工资源权限的服务端接口鉴权

当前只支持本地运行，不包含 Docker、生产部署、HTTPS、CI/CD、短信登录、单点登录和第三方登录。后端按 Java 全新实现，不迁移或兼容既有 Python 代码。

## 2. 技术选型

### 2.1 后端

- Java 17
- Spring Boot 3.x
- Maven 多模块
- Spring MVC
- Spring Validation
- MyBatis-Plus
- MySQL Connector/J
- Alibaba Druid Spring Boot 3 Starter
- Spring Data Redis（Lettuce）
- spring-security-crypto，仅使用 BCrypt 密码工具
- springdoc-openapi + Swagger UI
- JUnit 5、Spring Boot Test、Testcontainers
- MySQL 8.0，字符集使用 `utf8mb4`
- Redis 6.2+

标准单表 CRUD 使用 MyBatis-Plus，复杂关联查询、权限查询和树查询使用 Mapper XML。不得为了避免编写 SQL 而把复杂查询堆入条件构造器。

数据库连接池使用 Alibaba Druid，不启用 Druid 内置监控页面，避免增加额外管理入口。接口文档由 springdoc-openapi 根据 Spring MVC Controller 和注解自动生成，并通过 Swagger UI 展示。登录态使用 Redis 服务端会话，不使用 JWT，也不引入 Spring Security 框架；密码哈希仅使用 `spring-security-crypto` 提供的 BCrypt 工具。

### 2.2 前端

- Next.js
- React
- TypeScript
- Tailwind CSS
- shadcn/ui
- TanStack Query
- React Hook Form
- Zod
- Vitest
- React Testing Library

前端不引入 Redux。服务端数据由 TanStack Query 管理，表单状态由 React Hook Form 管理。

## 3. 总体架构

项目采用按技术层拆分的 Maven 多模块单体。系统能力和后续具体业务在每个技术层内使用同名子包组织，前后端独立安装和启动：

```text
浏览器
  ├── 加载页面 → Next.js :3000
  └── REST API → Spring Boot :8080
                          └── MyBatis-Plus → MySQL
```

仓库结构：

```text
admin-system/
├── backend/
│   ├── pom.xml
│   ├── admin-common/
│   ├── admin-entity/
│   ├── admin-dao/
│   ├── admin-service/
│   ├── admin-action/
│   ├── admin-boot/
│   └── sql/                      # 人工执行的数据库脚本
├── frontend/
├── docs/
├── scripts/
├── .gitignore
└── README.md
```

浏览器直接调用 Spring Boot。后端通过 CORS 仅允许配置的前端来源，并允许该来源携带 Cookie。

## 4. 后端 Maven 多模块结构

```text
backend/
├── pom.xml
├── admin-common/
│   └── src/main/java/com/oa/common/
│       ├── annotation/
│       ├── exception/
│       ├── response/
│       └── model/
│           ├── system/
│           │   ├── dto/            # 系统请求对象
│           │   ├── vo/             # 系统响应对象
│           │   └── enums/          # 系统业务枚举
│           └── order/              # 后续业务同样按 dto、vo、enums 分包
├── admin-entity/
│   └── src/main/java/com/oa/entity/
│       ├── system/
│       └── order/                  # 后续具体业务示例
├── admin-dao/
│   └── src/main/
│       ├── java/com/oa/dao/
│       │   ├── system/
│       │   └── order/
│       └── resources/
│           ├── mapper/
│           │   ├── system/
│           │   └── order/
├── admin-service/
│   └── src/main/java/com/oa/service/
│       ├── system/
│       │   └── impl/
│       └── order/
│           └── impl/
├── admin-action/
│   └── src/main/java/com/oa/action/
│       ├── system/
│       │   └── controller/
│       ├── order/
│       │   └── controller/
└── admin-boot/
    └── src/main/
        ├── java/com/oa/boot/
        │   ├── OaApplication.java
        │   ├── security/
        │   ├── config/
        │   └── advice/
        └── resources/
            ├── application.yml
            └── application-local.yml
```

每个子模块包含自己的 `pom.xml` 和测试目录，图中省略重复结构。

模块职责：

- `admin-common`：共享注解、统一响应、基础异常，以及统一模型目录；每个业务子包内再按 `dto`、`vo`、`enums` 区分请求对象、响应对象和业务枚举。
- `admin-entity`：MyBatis-Plus 实体和数据库字段映射，不保存 DTO、VO 或业务枚举。
- `admin-dao`：Mapper 和 Mapper XML，不承载业务规则。
- `admin-service`：具体 Service 类和事务，承载系统及具体业务逻辑；只有多个生产实现时才拆分接口，不再单独建立 `model` 包。
- `admin-action`：只保存 Controller，包路径统一使用 `com.oa.action.controller.<业务模块>`；不保存 DTO、VO、过滤器、拦截器、配置或异常处理。
- Service 默认使用具体类；没有对应接口时不建立 `impl` 包，配置类放 `config`，存储适配类放 `store` 等语义化子包。
- Redis 部署边界：当前登录会话仅支持单机 Redis。会话键与员工会话索引键由同一 Lua 脚本原子维护，不兼容 Redis Cluster 的跨键槽执行；启用集群前必须重新设计键槽和员工会话批量失效方案。
- `admin-boot`：Spring Boot 启动、运行配置和 Web 模块装配，承载过滤器、拦截器、CORS 配置和统一异常处理，不放具体业务逻辑。
- 系统能力放在各层的 `system` 子包；后续业务按稳定边界增加 `order`、`product` 等对应子包，不新增 Maven 业务模块。

### 4.1 分层职责

- `admin-action` 负责 HTTP 参数接收、校验、当前用户上下文和响应装配。
- `admin-service` 定义 Service 接口，`impl` 子包负责业务规则和事务边界；跨业务调用只调用对方 Service 接口。
- `admin-dao` 定义 MyBatis-Plus Mapper；复杂 SQL 放在同名 Mapper XML 中。
- `admin-entity` 映射数据库表，不直接作为接口请求或响应对象。
- Service 不直接构造 MyBatis-Plus `Wrapper` 或编写 SQL；所有数据访问通过语义化 Mapper 方法完成，复杂查询放 Mapper XML。
- DTO 放在 `admin-common/model/<业务>/dto`，VO 放在 `admin-common/model/<业务>/vo`，业务枚举放在 `admin-common/model/<业务>/enums`，Action 和 Service 共同使用。
- DTO、VO 和其他数据对象使用传统 Java Bean，不使用 `record` 或 Lombok；对象字段必须使用中文注释说明用途。
- 业务枚举统一定义 `code`、`name` 字段：数据库持久化使用 `code`，中文展示使用 `name`。
- Entity 只使用与数据库字段对应的原始 Java 类型，不直接引用业务枚举；状态等枚举转换由 Service 映射处理。
- Entity 中数据库 `NOT NULL` 的数值字段使用 Java 基本类型，允许 `NULL` 的数值字段使用包装类型；DTO、VO 按接口可空语义定义。
- 登录和 Token 会话规则放在 `admin-service/system`，Token 认证过滤器与接口鉴权拦截器放在 `admin-boot/security`。
- 统一异常类型放在 `admin-common`，异常到 HTTP 响应的转换放在 `admin-boot/advice`。

Service 按传统分层保留，但默认直接定义具体 Service 类；只有存在两个及以上生产实现时才拆分接口与实现。测试 Fake/Mock 不作为抽取接口的理由。Controller 不直接调用 Mapper，Mapper 不承载业务决策。简单 CRUD 不额外创建 Repository 层。

### 4.2 依赖规则

```text
admin-boot → admin-action → admin-service → admin-dao → admin-entity → admin-common
```

- Maven 模块依赖只能沿上述方向，不允许反向依赖或跨层绕过。
- `admin-common` 只放跨模块使用的基础类型，以及 `model/<业务>` 下的 DTO、VO 和业务枚举，禁止成为 `utils` 杂物模块。
- `admin-action` 只能调用 `admin-service`，不得直接调用 `admin-dao`。
- `admin-service` 可以调用本业务或其他业务的 Service 接口；跨业务不得调用对方 Mapper。
- `admin-dao` 只访问 `admin-entity`，不得依赖 Service 或 Action。
- 系统与具体业务通过 `admin-service` 中的接口协作，因此不会形成 Maven 循环依赖。
- 子包之间仍可能形成代码级循环；出现双向调用时应调整职责或使用中立的 Service 接口解耦，不得依赖 Spring `@Lazy` 掩盖问题。
- 各层的同一业务子包名称必须一致，便于从接口追踪到数据层。

## 5. 前端项目结构

```text
frontend/
├── src/
│   ├── app/
│   │   ├── (auth)/login/
│   │   └── (admin)/
│   │       ├── layout.tsx
│   │       ├── dashboard/
│   │       ├── system/
│   │       └── business/
│   ├── features/
│   │   ├── system/
│   │   │   ├── employees/
│   │   │   ├── departments/
│   │   │   ├── roles/
│   │   │   ├── resources/
│   │   │   └── api-catalog/
│   │   └── business/
│   ├── components/
│   │   ├── ui/
│   │   └── layout/
│   ├── lib/
│   │   ├── api-client.ts
│   │   ├── auth.ts
│   │   ├── permissions.ts
│   │   └── query-client.ts
│   └── types/api.ts
├── public/
├── .env.example
├── components.json
├── package.json
└── next.config.ts
```

- `app` 只负责路由、布局和页面装配。
- `features` 保存具体功能的表格、表单、API 调用和业务交互。
- `components/ui` 保存 shadcn/ui 基础组件。
- `components/layout` 保存侧边栏和顶部导航等全局布局。
- 功能专属组件不得放入全局 `components`。
- 新业务在 `app/(admin)/business` 和 `features/business` 下建立对应模块。

## 6. 数据模型

### 6.1 核心表

| 表 | 作用 |
|---|---|
| `t_employee` | 员工账号、密码哈希、部门、状态和超级管理员标记 |
| `t_department` | 通过 `parent_id` 构建部门树 |
| `t_role` | 角色编码、名称、描述和状态 |
| `t_system_resource` | 目录、菜单和按钮组成的资源树 |
| `t_system_api` | Spring MVC 提供的接口目录 |
| `t_employee_role` | 员工与角色多对多关联 |
| `t_role_resource` | 角色与资源多对多关联 |
| `t_resource_api` | 资源与接口多对多关联 |

所有主表包含创建时间和更新时间，关联表包含创建时间。当前不配置 MyBatis-Plus 自动填充器，Service 在写入时显式设置公共时间字段；建表 SQL 仍声明非空约束。

### 6.2 员工

员工包含用户名、姓名、密码哈希、手机号、邮箱、所属部门、启停状态、超级管理员标记、创建时间和更新时间。用户名唯一，密码只保存安全哈希。基础版本不包含工号、头像、性别、职位和入职日期。

### 6.3 系统资源

资源类型：

- `DIRECTORY`：导航分组，不对应页面。
- `MENU`：可访问的前端页面。
- `ACTION`：页面操作按钮，必须为叶子节点。

主要字段包括 `parent_id`、`type`、`name`、`code`、`path`、`icon`、`sort_order`、`visible` 和 `status`。

约束：

- `DIRECTORY` 不配置操作权限码。
- `MENU` 可以挂子菜单或操作按钮。
- `ACTION` 不能有子节点，不配置路由和图标。
- `MENU` 和 `ACTION` 的资源编码全局唯一。
- 删除资源前必须确认没有子节点，并检查角色和接口关联。
- 禁用父资源会使子树失效，但保留已有授权关系。

### 6.4 接口目录

`t_system_api` 主要字段：

- `name`
- `path`：Spring MVC 路由模板，同时作为接口资源标识
- `description`
- `status`

接口不再定义独立编码和 HTTP Method 字段，`path` 是唯一接口资源标识。鉴权时直接使用 Spring MVC 已匹配的路由模板定位接口，不读取请求方法。接口名称和描述取自 OpenAPI `@Operation`，路径取自 Spring MVC Handler 映射。管理端可以启停接口，但不能修改由代码同步的名称、路径和描述。接口仅在 `status = ENABLED` 时有效。

由于权限只按路径区分，不同接口必须使用不同路径，即使 HTTP 方法不同也不能复用同一路径。例如分页查询使用 `/employees/page`，新增使用 `/employees/create`，不得用 GET 和 POST 共用 `/employees` 表达两个独立权限。

### 6.5 完整表结构

以下 DDL 是基础版本的完整建表基线。所有表使用 InnoDB 和 `utf8mb4`，排序规则使用目标 MySQL 实例默认值，不在 DDL 中写死。`status` 统一约定 `0` 为禁用、`1` 为启用；关联关系不使用数据库外键，由 Service 事务校验引用完整性，避免删除和初始化时受到隐式级联影响。所有时间由应用写入，不依赖数据库默认时间。

```sql
CREATE TABLE t_department (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父部门ID，根部门为0',
    name VARCHAR(100) NOT NULL COMMENT '部门名称',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1启用',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_department_parent_name (parent_id, name),
    KEY idx_department_parent_sort (parent_id, sort_order),
    KEY idx_department_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门';

CREATE TABLE t_employee (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '员工ID',
    username VARCHAR(64) NOT NULL COMMENT '登录用户名',
    name VARCHAR(100) NOT NULL COMMENT '员工姓名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt密码哈希',
    phone VARCHAR(32) NULL COMMENT '手机号',
    email VARCHAR(255) NULL COMMENT '邮箱',
    department_id BIGINT UNSIGNED NOT NULL COMMENT '所属部门ID',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1启用',
    is_superuser TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否超级管理员：0否，1是',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_employee_username (username),
    UNIQUE KEY udx_employee_phone (phone),
    UNIQUE KEY udx_employee_email (email),
    KEY idx_employee_department_status (department_id, status),
    KEY idx_employee_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工';

CREATE TABLE t_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    code VARCHAR(64) NOT NULL COMMENT '角色编码',
    name VARCHAR(100) NOT NULL COMMENT '角色名称',
    description VARCHAR(500) NULL COMMENT '角色描述',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1启用',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_role_code (code),
    UNIQUE KEY udx_role_name (name),
    KEY idx_role_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE t_system_resource (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '资源ID',
    parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父资源ID，根资源为0',
    type VARCHAR(16) NOT NULL COMMENT '资源类型：DIRECTORY、MENU、ACTION',
    name VARCHAR(100) NOT NULL COMMENT '资源名称',
    code VARCHAR(100) NULL COMMENT '菜单或按钮资源编码',
    path VARCHAR(255) NULL COMMENT '前端菜单路由',
    icon VARCHAR(100) NULL COMMENT '图标名称',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    visible TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否可见：0否，1是',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1启用',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_system_resource_code (code),
    UNIQUE KEY udx_system_resource_path (path),
    KEY idx_system_resource_parent_sort (parent_id, sort_order),
    KEY idx_system_resource_type_status (type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统资源';

CREATE TABLE t_system_api (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '接口ID',
    name VARCHAR(100) NOT NULL COMMENT '接口名称',
    path VARCHAR(255) NOT NULL COMMENT 'Spring MVC路由模板，也是接口资源标识',
    description VARCHAR(500) NULL COMMENT '接口描述',
    status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1启用',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_system_api_path (path),
    KEY idx_system_api_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统接口目录';

CREATE TABLE t_employee_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '员工角色关联ID',
    employee_id BIGINT UNSIGNED NOT NULL COMMENT '员工ID',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_employee_role_employee_role (employee_id, role_id),
    KEY idx_employee_role_role_employee (role_id, employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工角色关联';

CREATE TABLE t_role_resource (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色资源关联ID',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    resource_id BIGINT UNSIGNED NOT NULL COMMENT '资源ID',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_role_resource_role_resource (role_id, resource_id),
    KEY idx_role_resource_resource_role (resource_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色资源关联';

CREATE TABLE t_resource_api (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '资源接口关联ID',
    resource_id BIGINT UNSIGNED NOT NULL COMMENT '资源ID',
    api_id BIGINT UNSIGNED NOT NULL COMMENT '接口ID',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_resource_api_resource_api (resource_id, api_id),
    KEY idx_resource_api_api_resource (api_id, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源接口关联';
```

字段与约束补充：

- `t_system_api` 的业务字段只有 `name`、`path`、`description`、`status`；`id` 和时间字段是关联及审计所需的技术字段。
- `t_department.parent_id = 0` 和 `t_system_resource.parent_id = 0` 表示根节点，树深度和环路由 Service 校验。
- `t_system_resource.code` 只用于 `MENU`、`ACTION`，`DIRECTORY` 写 `NULL`；`path` 只用于 `MENU`，其他类型写 `NULL`。
- MySQL 唯一索引允许多行 `NULL`，因此目录和按钮可以不填写菜单路径，目录可以不填写资源编码。
- 手机号和邮箱为空时必须写 `NULL`，不能写空字符串，否则唯一索引会把多个空字符串视为冲突。
- 三张关联表使用自增 `id` 主键，并使用两列唯一索引防止重复授权；反向索引用于角色反查、资源删除检查和接口权限查询。
- 删除员工、角色、资源或接口前由 Service 显式检查或删除关联记录，不依赖数据库级联删除。
- Redis 只保存登录会话，不保存角色、资源或接口授权关系，也不新增数据库会话表。

## 7. 授权模型

```text
员工 → 角色 → 资源（目录/菜单/按钮）→ 接口
```

角色只分配资源，不直接分配接口。资源通过 `t_resource_api` 维护可以调用的接口。同一接口可以关联多个资源。普通员工的有效资源和接口权限取所有启用角色授权的并集，不设计拒绝权限。

接口鉴权流程：

```text
请求到达
→ 命中公开路径白名单：直接放行
→ Token 认证过滤器读取 Cookie 中的 Token 并查询 Redis 会话
→ 会话有效：建立当前员工身份并将空闲过期时间续到 1 天
→ 命中仅登录路径白名单：登录有效后放行
→ 根据 Spring MVC 已匹配路由模板定位接口
→ 查询当前员工的有效角色、资源及关联接口
→ 存在当前接口且接口有效：放行
→ 否则：返回 403
```

安全规则：

- 后端不信任前端传递的资源编码或接口路径，只使用服务端已匹配的路由模板。
- 前端菜单和按钮控制只改善体验，后端接口鉴权才是安全边界。
- 登录、Swagger 等公开路径以及当前员工、退出等仅登录路径由服务端固定白名单管理，不进入接口资源目录。
- 除固定白名单外，所有 Controller 接口默认要求资源授权，不需要权限注解。
- 接口未关联任何资源时，普通员工一律不能访问。
- 禁用员工、角色、资源或接口后，权限立即失效。
- 超级管理员可以绕过普通资源授权，但必须登录，且不能访问已禁用接口。
- 基础版本不做权限缓存，直接查询数据库，优先保证一致性。

角色授权树中，父节点半选只用于界面展示。数据库保存实际选中的资源；返回导航时根据已授权菜单向上补齐有效父目录，不能因父节点半选而授予整个子树。

## 8. 接口扫描与同步

Controller 只使用 Spring MVC 映射和 `@Tag`、`@Operation` 等 OpenAPI 注解。接口路径就是接口资源标识，不再声明 `@ApiPermission`、独立接口编码或访问类型：

```java
@Operation(summary = "新增员工")
@PostMapping("/employees/create")
public EmployeeVO create(@Valid @RequestBody EmployeeCreateDTO request) {
    return employeeService.create(request);
}
```

本地同步命令由 Spring Boot Runner 调用 `RequestMappingHandlerMapping` 扫描 Handler，并读取 OpenAPI 注解补充接口名称和描述：

```bash
./mvnw -pl admin-boot -am spring-boot:run \
  -Dspring-boot.run.arguments=--app.command=sync-apis
```

同步规则：

- 新接口自动新增。
- 名称或描述变化时按路径更新。
- 路径变化视为新接口；旧路径状态改为 `DISABLED`，不直接删除接口或资源关联。
- 数据库存在但代码已删除的接口状态改为 `DISABLED`。
- 同步命令可重复执行，不产生重复数据。
- 每个路径只能对应一个 Handler；即使 HTTP 方法不同，重复路径也必须使同步失败。
- 同步过程不得自动重新启用已禁用接口；公开和仅登录白名单中的 Handler 不写入接口目录。
- `t_resource_api` 由管理端维护，同步过程不得自动授予权限。

## 9. 登录与安全

- 使用用户名和密码登录，密码使用 `spring-security-crypto` 的 `BCryptPasswordEncoder` 哈希；不启用 Spring Security 框架。
- 登录成功后使用 `SecureRandom` 生成至少 256 位不可预测的随机 Token，并编码为 Base64URL 无填充格式；Token 不包含员工信息。
- 登录成功后通过 `Set-Cookie` 写入唯一的会话 Token，不返回 Refresh Token、员工信息或权限数据；前端再通过当前员工接口获取展示信息和权限。
- Cookie 名称固定为 `ADMIN_TOKEN`，设置 `HttpOnly`、`Path=/` 和 `SameSite=Lax`；非本地 HTTPS 环境必须设置 `Secure`。前端请求启用 `credentials: "include"`，不读取或保存 Token。
- 项目不支持通过 `Authorization` 或其他自定义请求头传递 Token，也不得把 Token 放入 URL、响应正文、日志或错误信息。
- Redis 只使用 Token 的 SHA-256 哈希作为会话键，值使用明确的会话缓存对象保存员工 ID、用户名、姓名和超级管理员标记等最小登录信息，不使用 `Map` 表达缓存字段，也不直接将缓存对象作为接口响应。
- 会话键使用 `admin:session:<tokenHash>`，员工会话索引使用 `admin:employee-sessions:<employeeId>` Set；登录时写入索引，退出或失效时同步移除，索引中的过期成员在访问时惰性清理。
- Redis 会话空闲有效期为 1 天。`TokenAuthenticationFilter` 每次成功读取有效会话后将过期时间重新设置为 1 天；公开接口和无效 Token 不续期。
- `TokenAuthenticationFilter` 建立请求级当前员工上下文，请求结束时必须清理上下文。
- `ResourceApiAuthorizationInterceptor` 使用已匹配路由模板查找接口目录，再调用权限 Service 决定放行或返回 `401`、`403`。
- 服务端不创建 HTTP Session。
- 由于 Cookie 会由浏览器自动携带，新增、修改和删除等非安全请求必须校验 `Origin` 是否等于配置的前端来源；缺少 `Origin` 时校验 `Referer`，两者均无法确认来源时拒绝请求。
- 基础版本不增加第二个 CSRF Token；CSRF 防护依赖 `SameSite`、严格的来源校验和 CORS 白名单共同完成。
- 权限不写入 Redis 会话，每次按数据库中的有效员工、角色、资源和接口关系判断，保证授权变化立即生效。
- 员工禁用、删除或密码重置时，必须主动删除该员工的全部 Redis 会话；退出登录删除当前会话。
- Redis 不可用时登录和受保护接口失败关闭，返回服务不可用，不得绕过认证或降级为内存会话。
- 登录失败统一返回“用户名或密码错误”。
- 日志不得记录密码、Token、`Cookie` 或 `Set-Cookie` 请求头。
- 普通员工不能修改自己的角色、状态或超级管理员标记。
- 内置超级管理员禁止删除和禁用。

## 10. 页面与交互

基础页面：

```text
/login
/dashboard
/system/employees
/system/departments
/system/roles
/system/resources
/system/apis
```

- 员工管理：分页查询、新增、编辑、删除、启停、重置密码和分配角色。
- 部门管理：树形查询、新增、编辑、删除和启停。
- 部门新增或移动时，目标父部门及其祖先必须启用。
- 部门树最多允许 64 层，根部门深度为 1。
- 角色管理：分页查询、新增、编辑、删除、启停和分配资源。
- 资源管理：维护目录、菜单、按钮以及资源接口关联。
- 接口管理：查看同步结果、搜索和启停；代码所有字段只读。
- 左侧导航根据当前员工有效目录和菜单动态生成。
- 操作按钮根据当前员工有效 `ACTION` 资源编码展示。

## 11. 错误响应

统一响应格式：

```json
{
  "code": "EMPLOYEE_USERNAME_EXISTS",
  "message": "用户名已存在",
  "details": null
}
```

`@RestControllerAdvice` 统一转换参数校验、业务异常和系统异常。HTTP 状态码约定：

- `400`：违反业务规则
- `401`：未登录或登录态失效
- `403`：没有接口权限
- `404`：资源不存在
- `409`：唯一值冲突
- `422`：请求字段校验失败
- `503`：Redis 等认证基础设施不可用

## 12. 数据库脚本、初始化与本地运行

`application-local.yml` 通过环境变量读取本地配置：

```yaml
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    url: ${DB_URL:jdbc:mysql://127.0.0.1:3306/admin_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    druid:
      initial-size: 2
      min-idle: 2
      max-active: 20
      max-wait: 30000
      validation-query: SELECT 1
      test-while-idle: true
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DATABASE:0}
      timeout: 3s

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html

app:
  session-idle-timeout: 1d
  initial-admin-username: ${INITIAL_ADMIN_USERNAME:admin}
  initial-admin-password: ${INITIAL_ADMIN_PASSWORD}
  frontend-origin: ${FRONTEND_ORIGIN:http://localhost:3000}
```

Swagger UI 本地访问地址为 `http://localhost:8080/swagger-ui.html`，OpenAPI JSON 地址为 `http://localhost:8080/v3/api-docs`。这两个地址仅用于本地开发，由 `TokenAuthenticationFilter` 显式放行，不进入业务接口目录；生产环境是否开放不在当前方案范围内。

启动顺序：

1. 创建 MySQL 数据库 `admin_system`。
2. 人工审核并按编号顺序执行 `backend/sql/` 中尚未执行的 SQL。
3. 配置本地环境变量。
4. 启动 Redis，并确认连接配置可用。
5. 在 `backend` 执行 `./mvnw clean test`。
6. 启动 `admin-boot`；应用不得自动建表、改表或执行 SQL 文件。
7. 手动触发接口目录同步和基础数据初始化命令。
8. Spring Boot 监听 `localhost:8080`。
9. 安装前端依赖并启动 Next.js，监听 `localhost:3000`。

数据库变更 SQL 统一保存在 `backend/sql/`，按 `yyyyMMddNN-业务-说明.sql` 命名，例如 `2026072201-system-init.sql`。SQL 由人工确认目标环境、备份要求和执行顺序后操作，应用启动过程不得扫描或执行该目录。已经执行过的 SQL 不得修改；后续变更新增脚本。初始密码不得写入 SQL 文件。接口同步和业务初始化必须可以重复执行。

接口同步与数据初始化是两个事务阶段：同步阶段可独立提交；初始化阶段使用单事务，失败时只回滚该阶段且不得留下半成品。初始化不创建虚拟系统角色，超级管理员统一通过 `is_superuser` 绕过资源关联。首次初始化以管理员用户名唯一约束作为并发竞争点，冲突事务完整回滚后最多重试一次。

根部门按固定名称“总部”查找，系统管理根目录按固定名称和根节点查找；其余菜单与操作均按稳定资源编码查找。已有资源允许保留名称、路径、图标、排序、可见性和状态定制，但类型和父资源必须与初始化结构一致，否则整体回滚且不得追加接口关联。重复初始化只补缺失资源和关联，不覆盖展示字段，不删除额外关联；任何必需接口路径缺失时，初始化事务整体回滚。

`FRONTEND_ORIGIN` 只接受一个带主机的 HTTP 或 HTTPS origin，可包含端口；拒绝通配符、多 origin、userinfo、路径、查询参数和片段。单个尾斜杠在加载配置时移除，CORS 仅回显该规范化 origin。

当前终端找不到 `mysql` 命令。项目可以完成代码和自动化测试，但在 MySQL 客户端路径或连接配置可用前，SQL 人工执行和真实 MySQL 联调不能视为完成。

## 13. 事务与并发

- 事务边界统一放在 Service 实现方法，使用 `@Transactional`。
- Controller 和 Mapper 不开启业务事务。
- 简单单表查询优先在 Mapper 默认方法中使用 MyBatis-Plus，复杂查询和联表查询放 Mapper XML；Service 不构造查询 Wrapper。
- 多表授权保存和初始化必须在单事务内完成。
- 唯一性最终由数据库唯一索引保证，Service 的预检查只用于返回友好错误。
- 非必要不使用 `SELECT ... FOR UPDATE` 等数据库行锁。只有存在明确的并发一致性要求且普通事务、唯一索引或条件更新无法满足时才使用，并说明锁定范围和事务边界；禁止在持有数据库行锁期间调用 Redis、HTTP 等外部服务。
- 批量保存关联关系时，先校验全部目标对象，再统一写入，避免部分成功。

## 14. 测试与验收

后端测试：

- Service 单元测试覆盖核心业务规则。
- Controller 测试覆盖参数校验、状态码和统一错误响应。
- Mapper 集成测试使用 Testcontainers MySQL，不使用 H2 替代 MySQL 行为。
- 用户名密码登录成功与失败。
- 登录成功只通过 Cookie 下发一个随机 Token，不在响应正文返回员工信息、Token 或 Refresh Token。
- 有效 Token 可以恢复登录员工，连续活动会把 Redis 空闲有效期续到 1 天。
- 过期、伪造或已退出的 Token 返回 `401`，且不续期。
- 员工禁用、删除或密码重置后，其全部 Token 立即失效。
- Redis 不可用时登录和受保护接口返回 `503`，不能绕过认证。
- 禁用员工、角色、资源或接口后权限立即失效。
- 多角色资源和接口权限取并集。
- 绕过前端直接请求未授权接口返回 `403`。
- 接口同步可重复执行，并能新增路径、更新名称与描述、禁用已移除路径。
- 除固定白名单外，每个 Handler 都能按唯一 `path` 同步并参与资源鉴权；不同 HTTP 方法不得复用同一路径。
- 部门、资源等树形数据满足层级和删除约束。
- Maven 模块依赖方向符合第 4.2 节约束。

前端测试：

- 登录表单。
- 动态菜单生成。
- 按钮按资源编码显示或隐藏。
- 资源树编辑及接口关联。
- 角色授权树的选中、半选和保存行为。

本地验收标准：

1. 人工执行数据库 SQL 后，接口同步和初始化可以重复执行。
2. 初始管理员可以登录。
3. 可以维护员工、部门、角色、资源和接口关联。
4. 普通员工只能看到授权菜单和按钮。
5. 普通员工只能调用资源关联的服务端接口。
6. 直接请求未授权接口返回 `403`。
7. 新业务子包可以在不修改鉴权算法的情况下接入资源和接口授权。
8. 后端和前端可以分别通过本地命令启动。
9. 后端和前端自动化测试通过。

## 15. 新业务接入规则

以后增加订单等业务时：

1. 在 `admin-common/model`、`admin-entity`、`admin-dao`、`admin-service` 和 `admin-action` 中分别创建同名业务子包，例如 `order`。
2. DTO 放入 `admin-common/model/order/dto`，VO 放入 `admin-common/model/order/vo`，业务枚举放入 `admin-common/model/order/enums`；实体放入 `admin-entity/order`，Mapper 放入 `admin-dao/order`，业务服务放入 `admin-service/order`，Web 接口放入 `admin-action/order`。
3. 在 `backend/sql/` 增加按日期和序号命名的人工执行 SQL，不得修改已执行脚本。
4. 为每个 Controller 接口声明稳定路由，并使用 OpenAPI 注解维护接口名称和描述。
5. 执行接口同步，在资源管理中创建业务菜单和按钮并关联接口。
6. 在角色管理中授权业务资源。
7. 在前端 `app/(admin)/business` 和 `features/business` 下创建对应功能。

新增业务不得修改通用鉴权算法。`admin-common/model/<业务>` 只允许增加 `dto`、`vo`、`enums` 子包，不得放入业务实现；`admin-boot` 不得放入具体业务代码。跨业务调用必须通过 `admin-service` 中的 Service 接口，不得直接访问其他业务子包的 Mapper 和数据表。
