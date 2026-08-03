# Java 基础后台管理系统设计

> 设计状态：2026-07-31 已确认权限缓存重构目标，代码和数据库变更尚未实施；实施完成前，现有权限缓存代码仍代表运行事实。

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

标准单表 CRUD 使用 MyBatis-Plus。数据访问优先拆为语义明确的单表查询；权限链按员工、员工角色、启用角色、角色资源、有效资源及父资源、资源接口逐步查询，不使用一条多表 SQL。只有单表查询无法合理完成时才使用 Mapper XML 关联查询，且单条查询最多关联 3 张表。不得为了避免编写 SQL 而把复杂查询堆入 Service 条件构造器。

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
│           │   ├── enums/          # 系统业务枚举
│           │   └── cache/          # 系统缓存对象
│           └── order/              # 后续业务按模型用途建立语义子包
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
│       │   ├── config/
│       │   ├── store/
│       │   └── *Service.java
│       └── order/                  # 后续具体业务示例
├── admin-action/
│   └── src/main/java/com/oa/action/
│       └── controller/
│           ├── system/
│           └── order/              # 后续具体业务示例
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

- `admin-common`：共享注解、统一响应、基础异常，以及唯一的共享模型目录；每个业务子包内按 `dto`、`vo`、`enums`、`cache` 等用途建立语义子包。
- `admin-entity`：MyBatis-Plus 实体和数据库字段映射，不保存 DTO、VO 或业务枚举。
- `admin-dao`：Mapper 和 Mapper XML，不承载业务规则。
- `admin-service`：具体 Service 类和事务，承载系统及具体业务逻辑；只有多个生产实现时才拆分接口，不得单独建立 `model` 包或定义数据模型。
- `admin-action`：只保存 Controller，包路径统一使用 `com.oa.action.controller.<业务模块>`；不保存 DTO、VO、过滤器、拦截器、配置或异常处理。
- Service 默认使用具体类；没有对应接口时不建立 `impl` 包，配置类放 `config`，存储适配类放 `store` 等语义化子包。
- Redis 部署边界：当前登录会话通过 Lua 跨键维护会话和员工会话索引，仅支持单机 Redis；员工与角色授权关系使用独立 String 缓存并通过普通 `GET`、`MGET` 和 `DEL` 访问，不依赖跨键 Lua。启用 Redis Cluster 前仍须重新设计会话索引键槽和批量失效方案。
- `admin-boot`：Spring Boot 启动、运行配置和 Web 模块装配，承载过滤器、拦截器、CORS 配置和统一异常处理，不放具体业务逻辑。
- 系统能力放在各层的 `system` 子包；后续业务按稳定边界增加 `order`、`product` 等对应子包，不新增 Maven 业务模块。

### 4.1 分层职责

- `admin-action` 负责 HTTP 参数接收、校验、读取当前用户上下文和响应装配；当前用户上下文由 `admin-common` 提供，Controller 不自行保存上下文。
- `admin-service` 默认定义具体 Service 类并负责业务规则和事务边界；只有存在多个生产实现时才抽取接口，跨业务调用只调用对方 Service 公开方法。
- `admin-dao` 定义 MyBatis-Plus Mapper；复杂 SQL 放在同名 Mapper XML 中。
- `admin-entity` 映射数据库表，不直接作为接口请求或响应对象。
- 自定义类型应避免与 Spring、Swagger 等常用框架类型重名，优先使用语义清晰且不冲突的名称；除确有歧义且无法合理改名外，不在代码中反复使用全限定类名。统一接口响应类型命名为 `ApiResult`，Swagger 响应注解正常导入 `io.swagger.v3.oas.annotations.responses.ApiResponse`。
- Service 不直接构造 MyBatis-Plus `Wrapper` 或编写 SQL；所有数据访问通过语义化 Mapper 方法完成。数据访问优先拆为单表查询；只有单表查询无法合理完成时才允许关联查询，单条关联查询最多涉及 3 张表，确实需要超过 3 张表时必须先向用户提出单独讨论，未经确认不得实现。
- DTO 放在 `admin-common/model/<业务>/dto`，VO 放在 `admin-common/model/<业务>/vo`，业务枚举放在 `admin-common/model/<业务>/enums`，缓存对象等其他模型放在对应业务的语义子包，Action 和 Service 共同使用；其他模块不得自行建立 `model` 包或定义数据模型。
- DTO、VO 和其他数据对象使用传统 Java Bean：普通 `class`、`private` 字段、公开无参构造和显式标准 Getter/Setter，不使用 `record` 或 Lombok；可根据实际用途增加带参构造器或链式 Setter，但不添加无必要写法。对象字段必须使用中文注释说明用途。
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
- `admin-common` 只放跨模块使用的基础类型，以及 `model/<业务>/<语义子包>` 下的全部共享模型，禁止成为 `utils` 杂物模块。
- `admin-action` 只能调用 `admin-service`，不得直接调用 `admin-dao`。
- `admin-service` 可以调用本业务或其他业务的具体 Service 公开方法；跨业务不得调用对方 Mapper。
- `admin-dao` 只访问 `admin-entity`，不得依赖 Service 或 Action。
- 系统与具体业务通过 `admin-service` 中的公开方法协作，因此不会形成 Maven 循环依赖。
- 子包之间仍可能形成代码级循环；出现双向调用时应调整职责或提取中立的 Service 能力解耦，不得依赖 Spring `@Lazy` 掩盖问题。
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
| `t_system_version` | 按版本编码持久化系统单调版本，权限快照使用 `permission_snapshot` |

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

接口不再定义独立编码和 HTTP Method 字段，`path` 是唯一接口资源标识。鉴权时直接使用 Spring MVC 已匹配的路由模板定位接口，不读取请求方法。接口名称、路径和描述由 `backend/sql/` 下的数据 SQL 维护。管理端只提供接口目录分页、详情和启停能力，不能新增、修改或删除 SQL 维护的名称、路径和描述。接口仅在 `status = ENABLED` 时有效。

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

CREATE TABLE t_system_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '系统版本ID',
    version_code VARCHAR(64) NOT NULL COMMENT '版本编码',
    version_value BIGINT UNSIGNED NOT NULL COMMENT '版本值',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY udx_system_version_code (version_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统版本';
```

字段与约束补充：

- `t_system_api` 的业务字段只有 `name`、`path`、`description`、`status`；`id` 和时间字段是关联及审计所需的技术字段。
- `t_department.parent_id = 0` 和 `t_system_resource.parent_id = 0` 表示根节点，树深度和环路由 Service 校验。
- `t_system_resource.code` 只用于 `MENU`、`ACTION`，`DIRECTORY` 写 `NULL`；`path` 只用于 `MENU`，其他类型写 `NULL`。
- MySQL 唯一索引允许多行 `NULL`，因此目录和按钮可以不填写菜单路径，目录可以不填写资源编码。
- 手机号和邮箱为空时必须写 `NULL`，不能写空字符串，否则唯一索引会把多个空字符串视为冲突。
- 三张关联表使用自增 `id` 主键，并使用两列唯一索引防止重复授权；反向索引用于角色反查、资源删除检查和接口权限查询。
- 删除员工、角色、资源或接口前由 Service 显式检查或删除关联记录，不依赖数据库级联删除。
- `t_system_version` 按唯一 `version_code` 管理系统版本，权限快照固定使用 `permission_snapshot`，初始版本为 `0`；快照内容实际变化时使用单表原子更新执行 `version_value = version_value + 1`，不得先读后写覆盖并发版本。该表不保存权限明细，也不参与普通员工鉴权查询。
- Redis 保存登录会话、员工状态及角色关系、角色状态及资源关系，并保存资源权限快照的共享版本镜像；JVM 永久缓存资源、资源接口和启用接口路径组成的不可变快照。权限事实和快照持久版本仍以数据库为准，不新增数据库会话表。

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
→ 读取 Redis 员工授权关系缓存
→ 使用 MGET 批量读取员工关联的角色授权关系缓存
→ 员工或角色缓存缺失：通过语义化单表查询加载并回填 10 分钟缓存
→ 校验本地资源权限快照版本，落后时触发单线程后台重建，当前请求继续使用旧快照
→ 在 JVM 内合并有效角色资源、资源树和启用接口路径
→ 当前路由模板存在于员工启用接口路径集合：放行
→ 否则：返回 403
```

安全规则：

- 后端不信任前端传递的资源编码或接口路径，只使用服务端已匹配的路由模板。
- 前端菜单和按钮控制只改善体验，后端接口鉴权才是安全边界。
- 登录、Swagger 等公开路径以及当前员工、退出等仅登录路径由服务端固定白名单管理，不进入接口资源目录。
- 除固定白名单外，所有 Controller 接口默认要求资源授权，不需要权限注解。
- 接口未关联任何资源时，普通员工一律不能访问。
- 员工授权关系键为 `admin:employee-auth:<employeeId>`，值使用明确缓存对象保存员工状态、超级管理员标记和角色 ID 集合，TTL 固定为 10 分钟；员工状态、超级管理员标记或员工角色实际变化后，在数据库事务提交后删除对应缓存。角色授权关系键为 `admin:role-auth:<roleId>`，值保存角色状态和资源 ID 集合，TTL 同为 10 分钟；角色状态或角色资源实际变化后，在提交后删除对应缓存。
- 员工和角色授权关系采用 Cache Aside。缓存删除失败必须记录错误，并允许依靠 10 分钟 TTL 最终收敛；缓存删除与并发回填可能使权限变化最多延迟 10 分钟生效，这是本系统明确接受的最终一致窗口。员工禁用、删除、密码重置和超级管理员标记变化仍必须立即失效该员工全部登录会话。
- 员工权限不再以完整接口集合或资源树写入 Redis。普通员工先读取员工角色，再通过 `MGET` 批量读取角色资源，最后在 JVM 内与资源权限快照合并；多角色资源和接口取并集。超级管理员不读取角色资源，直接使用快照中的全部有效资源和启用接口，但仍必须登录且不能访问禁用或未登记接口。
- 一次 `MGET` 中的多个角色缓存缺失时，必须通过按 ID 集合组织的语义化单表批量查询一次加载缺失角色及其资源关系，再按角色拆分回填；禁止按角色循环查询数据库形成 N+1。数据库查询集合按项目批量上限分批处理。
- 资源权限快照包括资源全部展示与权限字段、完整父子关系、资源接口关联及启用接口路径。快照使用不可变对象永久驻留 JVM，由 `AtomicReference` 一次性发布；禁止为刷新快照设置固定 TTL，也禁止修改已发布对象内部的集合。
- 应用启动使用 `ApplicationRunner` 同步加载数据库一致性视图中的版本和完整快照，随后将该版本单调推进到 Redis 镜像并再次核对数据库持久版本；只有本地快照版本与数据库持久版本一致才允许启动完成。数据库或 Redis 不可用、快照加载失败或有限重试后版本仍持续变化时启动失败，禁止把首次快照加载推迟到业务请求。
- 数据库保存单调递增的资源快照持久版本，Redis 键 `admin:permission:snapshot:version` 是多实例共享镜像。任何会改变快照内容的资源新增、修改、删除、资源接口关联变化、接口状态或接口路径变化，都必须在同一数据库事务内递增持久版本；名称、编码、前端路径、图标、排序和可见性也属于快照字段，实际变化时同样递增版本。
- 数据库事务提交后立即把持久版本同步到 Redis，镜像只能向更大版本推进，不得被并发回调覆盖为旧值。Redis 重启、提交后同步失败或回调乱序时，由每个实例的统一快照刷新任务重新推进镜像。
- 每个实例只运行一个 `PermissionSnapshotRefreshTask`，每 5 秒读取 `t_system_version` 中 `permission_snapshot` 的版本并与本地快照比较。版本相同时直接结束；版本不一致时先推进 Redis 镜像，再通过原子刷新标记只向单线程执行器提交一个后台重建任务。权限计算只读取 JVM 本地快照，不访问 Redis 或数据库检查快照版本。当前请求和其他并发请求立即使用启动时已加载的旧快照，不等待重建。写事务所在实例提交并推进 Redis 版本后立即提交本实例后台刷新，其他实例最多 5 秒发现变化。后台任务从数据库一致性视图加载版本及完整快照，发布前再次读取数据库持久版本；版本一致才原子替换，版本变化或加载失败则保留旧快照、记录错误并清除刷新标记，由后续定时任务再次触发。运行期本地快照为空属于非法状态，鉴权返回 `503` 并记录错误，不得在请求线程补做首次加载。
- 后台任务框架参考 `cn.mucang.paas.commons.utils.Task`：本项目定义 `Task.run()` 和 `AbstractTask` 模板，统一提供 `preCheck()`、同实例防重入、执行耗时日志和异常处理。具体任务放在 `com.oa.service.task` 下并保持一个任务一个类，`@Scheduled` 只能出现在具体任务类，禁止放入业务 Service。Spring Bean 名是任务名称；`GET /api/admin/task/list.htm` 返回任务名称列表，`POST /api/admin/task/run.htm` 接收 `name` 和可选 `data` 并异步调用同一个 `Task.run()`，任务线程结束必须清理 `TaskContext`。
- Redis 授权缓存、版本镜像读取或反序列化异常统一转换为认证基础设施异常并返回 `503`，不得降级为每请求直接查询数据库，也不得用 `403` 掩盖基础设施故障。普通缓存未命中是允许查询对应单表并回填的正常状态。
- 超级管理员标记同时存在于登录会话中；修改该标记时，除删除员工授权关系缓存外，还必须调用 `SessionService.invalidateEmployeeSessions()` 使该员工全部旧会话失效。

角色授权树中，父节点半选只用于界面展示。数据库保存实际选中的资源；返回导航时根据已授权菜单向上补齐有效父目录，不能因父节点半选而授予整个子树。

## 8. 接口目录维护

Controller 只使用 Spring MVC 映射和 `@Tag`、`@Operation` 等 OpenAPI 注解。接口路径就是接口资源标识，不再声明 `@ApiPermission`、独立接口编码或访问类型：

```java
@Operation(summary = "新增员工")
@PostMapping("/employees/create")
public EmployeeVO create(@Valid @RequestBody EmployeeCreateDTO request) {
    return employeeService.create(request);
}
```

接口目录不从 Controller 扫描，不提供运行时同步命令。新增、删除或修改受保护 Controller 接口时，开发人员必须同步新增一份 `backend/sql/` 数据变更 SQL，显式维护接口目录、系统资源和资源接口关联。公开和仅登录白名单中的 Handler 不写入接口目录。

数据 SQL 按路径幂等补充接口，按资源编码幂等补充资源，并按资源编码和接口路径补充关联；不得覆盖管理端已经维护的接口状态和资源展示字段。已经执行过的 SQL 不得修改，接口路径变化必须通过新 SQL 明确处理旧路径和新路径。

## 9. 登录与安全

- 使用用户名和密码登录，密码使用 `spring-security-crypto` 的 `BCryptPasswordEncoder` 哈希；不启用 Spring Security 框架。
- 登录成功后使用 `SecureRandom` 生成至少 256 位不可预测的随机 Token，并编码为 Base64URL 无填充格式；Token 不包含员工信息。
- 登录成功后通过 `Set-Cookie` 写入唯一的会话 Token，不返回 Refresh Token、员工信息或权限数据；前端再通过当前员工接口获取展示信息和权限。
- Cookie 名称固定为 `ADMIN_TOKEN`，设置 `HttpOnly`、`Path=/` 和 `SameSite=Lax`；非本地 HTTPS 环境必须设置 `Secure`。前端请求启用 `credentials: "include"`，不读取或保存 Token。
- 项目不支持通过 `Authorization` 或其他自定义请求头传递 Token，也不得把 Token 放入 URL、响应正文、日志或错误信息。
- Redis 只使用 Token 的 SHA-256 哈希作为会话键，值使用明确的会话缓存对象保存员工 ID、用户名、姓名和超级管理员标记等最小登录信息，不使用 `Map` 表达缓存字段，也不直接将缓存对象作为接口响应。
- 会话键使用 `admin:session:<tokenHash>`，员工会话索引使用 `admin:employee-sessions:<employeeId>` Set；登录时写入索引，退出或失效时同步移除，索引中的过期成员在访问时惰性清理。
- Redis 会话空闲有效期为 1 天。`TokenAuthenticationFilter` 每次成功读取有效会话后将过期时间重新设置为 1 天；公开接口和无效 Token 不续期。
- `TokenAuthenticationFilter` 在认证成功后将当前员工写入 `admin-common` 提供的普通 `ThreadLocal` 请求上下文，并在过滤器 `finally` 中无条件清理。Controller 和 `ResourceApiAuthorizationInterceptor` 从该上下文读取当前员工，不再通过 `HttpServletRequest` 属性传递；Service 保持显式接收当前员工或员工 ID，不直接依赖线程上下文。上下文不得使用 `InheritableThreadLocal`，也不得传播到异步任务或子线程。
- `ResourceApiAuthorizationInterceptor` 使用已匹配路由模板查找接口目录，再调用权限 Service 决定放行或返回 `401`、`403`。
- 服务端不创建 HTTP Session。
- 由于 Cookie 会由浏览器自动携带，新增、修改和删除等非安全请求必须校验 `Origin` 是否等于配置的前端来源；缺少 `Origin` 时校验 `Referer`，两者均无法确认来源时拒绝请求。
- 基础版本不增加第二个 CSRF Token；CSRF 防护依赖 `SameSite`、严格的来源校验和 CORS 白名单共同完成。
- 权限不写入登录会话；员工与角色授权关系分别使用 10 分钟 Redis 缓存，接口路径和有效资源树在请求时通过角色资源与本地资源权限快照合成。资源快照永久驻留 JVM，并由数据库持久版本和 Redis 版本镜像驱动多实例刷新。
- 员工禁用、删除或密码重置时，必须主动删除该员工的全部 Redis 会话；退出登录删除当前会话。
- Redis 不可用时登录和受保护接口失败关闭，返回服务不可用，不得绕过认证或降级为内存会话。
- 登录失败统一返回“用户名或密码错误”。
- 日志不得记录密码、Token、`Cookie` 或 `Set-Cookie` 请求头。
- 普通员工不能修改自己的角色、状态或超级管理员标记。
- 基础版没有单独的 `built_in` 字段，所有 `is_superuser = 1` 的现有员工都按受保护超级管理员处理，禁止降级、删除和禁用。
- 员工禁止删除自己。

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
- 禁用部门前必须确认其全部子孙部门均已禁用；存在任意启用子孙时拒绝操作，不自动级联禁用。
- 部门新增、移动、启停和删除使用 `READ_COMMITTED` 事务及最小范围行锁；按部门 ID 升序锁定当前部门和目标父级祖先链，锁后重载部门图并重新校验，避免新增子部门、移动和删除父部门之间的并发完整性问题。持锁期间不调用 Redis 或 HTTP 外部服务。
- 部门树最多允许 64 层，根部门深度为 1。
- 角色管理：分页查询、新增、编辑、删除、启停和分配资源。
- 资源管理：维护目录、菜单、按钮以及资源接口关联。
- 接口管理：查看同步结果、搜索和启停；代码所有字段只读。
- 左侧导航根据当前员工有效目录和菜单动态生成。
- 操作按钮根据当前员工有效 `ACTION` 资源编码展示。

## 11. JSON 响应与错误处理

所有对外 JSON 接口响应的最外层统一使用 `ApiResult<T>`，并固定包含 `success`、`code`、`message`。普通数据通过 `details` 承载；分页响应使用 `ApiResult<PageResult<T>>`，`PageResult<T>` 只承载 `records`、`total`、`page`、`size`。由于后续不定义其他响应包装对象，项目不为响应对象抽取公共基类。文件下载和流式响应不受此 JSON 结构约束。

普通成功响应格式：

```json
{
  "success": true,
  "code": 0,
  "message": "成功",
  "details": {}
}
```

分页成功响应格式：

```json
{
  "success": true,
  "code": 0,
  "message": "成功",
  "details": {
    "records": [],
    "total": 0,
    "page": 1,
    "size": 20
  }
}
```

错误响应格式：

```json
{
  "success": false,
  "code": 1003,
  "message": "无权访问该接口",
  "details": null
}
```

统一响应和业务异常使用 `ExceptionCode` 枚举；`code` 是全局唯一数字，`name` 是中文默认消息。`success` 必须由响应码统一确定：仅 `ExceptionCode.SUCCESS` 对应 `true`，其他响应码一律对应 `false`，业务代码不得分别设置这两个字段。客户端默认通过 `success` 区分成功与失败，只在需要识别特定错误时判断 `code`。需要携带路径等上下文时允许覆盖响应消息，但不得散落字符串错误码。`@RestControllerAdvice` 统一转换参数校验、业务异常和系统异常。HTTP 状态码约定：

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
  security:
    frontend-origin: ${FRONTEND_ORIGIN:http://localhost:3000}
    cookie-secure: ${COOKIE_SECURE:false}
```

Swagger UI 本地访问地址为 `http://localhost:8080/swagger-ui.html`，OpenAPI JSON 地址为 `http://localhost:8080/v3/api-docs`。这两个地址仅用于本地开发，由 `TokenAuthenticationFilter` 显式放行，不进入业务接口目录；生产环境是否开放不在当前方案范围内。

启动顺序：

1. 创建 MySQL 数据库 `admin_system`。
2. 人工审核并按编号顺序执行 `backend/sql/` 中尚未执行的 SQL。
3. 配置本地环境变量。
4. 启动 Redis，并确认连接配置可用。
5. 在 `backend` 执行 `./mvnw clean test`。
6. 启动 `admin-boot`；应用不得自动建表、改表或执行 SQL 文件。
7. Spring Boot 监听 `localhost:8080`，启动过程不扫描接口或初始化基础数据。
8. 安装前端依赖并启动 Next.js，监听 `localhost:3000`。

数据库变更 SQL 统一保存在 `backend/sql/`，按 `yyyyMMddNN-业务-说明.sql` 命名，例如结构脚本 `2026072201-system-init.sql` 和数据脚本 `2026073001-system-data-init.sql`。SQL 由人工确认目标环境、备份要求和执行顺序后操作，应用启动过程不得扫描 Controller、创建基础数据或执行该目录。已经执行过的 SQL 不得修改；后续变更新增脚本。

基础数据 SQL 负责根部门、默认超级管理员、接口目录、系统资源和资源接口关联。管理员仅保存 BCrypt 哈希，不在 SQL 中保存明文密码。数据 SQL 按部门名称、员工用户名、接口路径、资源编码和关联唯一键重复执行不新增重复数据，不创建虚拟系统角色；超级管理员统一通过 `is_superuser` 获取全部启用接口权限。

`FRONTEND_ORIGIN` 只接受一个带主机的 HTTP 或 HTTPS origin，可包含端口；拒绝通配符、多 origin、userinfo、路径、查询参数和片段。单个尾斜杠在加载配置时移除，CORS 仅回显该规范化 origin。

当前终端找不到 `mysql` 命令。项目可以完成代码和自动化测试，但在 MySQL 客户端路径或连接配置可用前，SQL 人工执行和真实 MySQL 联调不能视为完成。

## 13. 事务与并发

- 事务边界统一放在 Service 实现方法，使用 `@Transactional`。
- Controller 和 Mapper 不开启业务事务。
- 简单单表查询优先在 Mapper 默认方法中使用 MyBatis-Plus；只有单表查询无法合理完成时才使用 Mapper XML 关联查询，单条关联查询最多涉及 3 张表，超过时必须先单独评审；Service 不构造查询 Wrapper。
- 多表授权保存和初始化必须在单事务内完成。
- 唯一性最终由数据库唯一索引保证，Service 的预检查只用于返回友好错误。
- 非必要不使用 `SELECT ... FOR UPDATE` 等数据库行锁。只有存在明确的并发一致性要求且普通事务、唯一索引或条件更新无法满足时才使用，并说明锁定范围和事务边界；禁止在持有数据库行锁期间调用 Redis、HTTP 等外部服务。
- 由于项目明确不使用数据库外键，员工删除和员工角色保存先锁目标员工行，员工角色保存随后按 ID 升序锁角色行；角色删除和角色资源保存先锁角色行，角色资源保存再按 ID 升序锁资源行。非根资源新增锁父资源，资源修改按 ID 升序锁当前资源和新父资源，资源状态修改、资源接口保存和资源删除锁目标资源；资源操作不反向获取角色锁。空集合不执行集合锁查询，持锁后仅执行数据库操作；员工和角色授权缓存只能在事务提交后删除。普通详情、资源树和分页等只读查询不加锁。
- 资源新增和移动采用“乐观快照 + 锁后校验”：先从单表资源图解析目标完整祖先链，再按 ID 升序锁定当前资源及完整祖先链；锁后核对当前资源的父级、状态、类型以及每层祖先的父链接、状态和类型。快照后发生任何并发语义变化时抛出 `RESOURCE_CONCURRENT_MODIFICATION` 并回滚，禁止静默覆盖。快照内容实际变化时在同一数据库事务内递增资源快照持久版本，锁后不得访问 Redis 等外部服务。
- 资源修改完成当前资源与完整祖先链的锁后校验后，必须再次执行一次有序单表资源查询，并使用锁后最新资源图校验现有子节点类型、移动环路和整棵子树深度，防止锁等待期间插入或移动的子节点绕过约束。
- 资源新增和修改事务单独声明 `READ_COMMITTED` 隔离级别，使等待父资源或当前资源行锁后的普通二次资源图查询能够读取等待期间已经提交的数据。禁止依赖 MySQL 默认 `REPEATABLE_READ` 的一致性读假装获得锁后最新资源图；父资源和当前资源行锁负责阻止二次读取之后的新建或移动写入，不使用全表锁，也不改变全局事务隔离级别。真实 MySQL 下的等待与可见性仍作为并发集成测试门禁。
- 资源接口保存先读取现有关联快照并按集合比较。集合相同时不递增快照版本，锁资源后再次读取仍相同则直接返回，不执行删除或插入；二次读取发现并发变化时抛出并发修改异常。集合不同时锁资源并按每批最多 500 个接口完成全量校验和替换，同时在数据库事务内递增资源快照持久版本。
- 资源接口保存只接受保存当时已启用的接口；接口后续被禁用时保留已有资源关联，鉴权重建会忽略禁用接口，因此不需要在资源接口保存时锁定系统接口行。
- 员工角色和角色资源关联使用 Mapper XML 单表多 `VALUES` 批量插入，Service 每批最多 500 条，空集合不执行插入 SQL。
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
- 员工禁用通过会话失效立即阻断旧会话；员工角色、角色状态和角色资源变化允许最多 10 分钟最终一致。资源与接口变化在持久版本同步到 Redis 后触发各实例快照刷新，提交后同步失败由 30 秒版本校准兜底。
- 员工与角色缓存命中、单个缺失和批量缺失均能正确回填，批量角色缺失不会产生按角色 N+1 查询。
- 本地快照版本命中时不查询资源权限基础表；版本变化时同一实例只提交一个后台重建任务，所有请求继续使用旧快照且不等待，发布前版本再次变化会丢弃重建结果。
- 多实例通过 Redis 版本镜像发现快照变化；事务后同步失败、回调乱序和 Redis 重启后，数据库版本校准能在 30 秒内恢复正确镜像且版本不会倒退。
- 后台重建失败或版本持续变化必须继续使用启动时已加载的旧快照并由后续请求重试，不返回 `503`；启动阶段首次加载失败必须阻止应用启动，运行期只有 Redis 授权数据不可读或本地快照意外为空时返回 `503`。
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

1. 人工按顺序执行结构 SQL 和数据 SQL，重复执行数据 SQL 不产生重复数据。
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
2. DTO 放入 `admin-common/model/order/dto`，VO 放入 `admin-common/model/order/vo`，业务枚举放入 `admin-common/model/order/enums`，缓存对象等其他共享模型放入对应语义子包；实体放入 `admin-entity/order`，Mapper 放入 `admin-dao/order`，业务服务放入 `admin-service/order`，Web 接口放入 `admin-action/controller/order`。
3. 在 `backend/sql/` 增加按日期和序号命名的人工执行 SQL，不得修改已执行脚本。
4. 为每个 Controller 接口声明稳定路由，并使用 OpenAPI 注解维护接口名称和描述。
5. 执行接口同步，在资源管理中创建业务菜单和按钮并关联接口。
6. 在角色管理中授权业务资源。
7. 在前端 `app/(admin)/business` 和 `features/business` 下创建对应功能。

新增业务不得修改通用鉴权算法。所有跨模块共享模型必须放在 `admin-common/model/<业务>/<语义子包>`，其中可按需要增加 `dto`、`vo`、`enums`、`cache` 等子包，但不得放入业务实现；`admin-boot` 不得放入具体业务代码。跨业务调用必须通过 `admin-service` 中具体 Service 的公开方法，不得直接访问其他业务子包的 Mapper 和数据表。
