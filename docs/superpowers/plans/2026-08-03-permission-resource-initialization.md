# 权限资源初始化实施计划

> **历史说明：** 本计划中的 `2026080301-system-data-reinit.sql` 已由 `backend/sql/data.sql` 取代，仅保留正文作为实施记录。

> **执行要求：** 按任务顺序执行并持续更新复选框状态；行为修改遵循测试驱动。项目未要求 Git 提交，因此计划不包含 commit、push 或 merge。

**目标：** 提供一份适用于空库重建的完整系统数据初始化 SQL，使菜单自带页面基础查询权限，每个操作资源包含完成操作所需的全部接口。

**实现方式：** 保留已执行过的历史 SQL，新建 `2026080301-system-data-reinit.sql` 作为新的完整初始化基线。SQL 分别维护接口定义、资源定义和资源接口定义三张临时表，以多对多映射表达菜单及操作的完整接口依赖；结构测试从 SQL 中解析映射并核对资源语义与受保护接口覆盖。

**技术栈：** MySQL 8、Java 17、JUnit 5、Spring Boot 3、Maven

---

## 文件范围

- 新建：`backend/sql/2026080301-system-data-reinit.sql`，空库重建时使用的完整基础数据。
- 修改：`backend/admin-boot/src/test/java/com/oa/boot/config/SqlInitializationTest.java`，验证完整接口、资源及多对多关联。
- 修改：`AGENTS.md`，固化菜单基础查询与操作完整依赖规则。
- 修改：`docs/superpowers/specs/2026-07-21-java-admin-system-design.md`，同步长期权限资源语义和初始化执行顺序。
- 修改：`docs/superpowers/specs/2026-08-03-permission-resource-initialization-design.md`，将设计状态更新为已实施并指向精确 SQL。

## 任务一：用失败测试锁定正确的资源模型

- [ ] 修改 `SqlInitializationTest`，将基础数据测试目标切换到 `2026080301-system-data-reinit.sql`。
- [ ] 在测试中定义完整受保护接口集合，包含现有 34 个系统接口以及任务列表、执行任务两个接口。
- [ ] 增加 SQL 映射解析辅助方法，从 `tmp_resource_api_definition` 的 `(resource_code, api_path)` 数据中读取资源接口关联。
- [ ] 增加断言：以下技术性资源编码不得存在：

```text
system:employee:page
system:employee:detail
system:employee:role-ids
system:department:tree
system:department:detail
system:role:page
system:role:detail
system:role:resource-ids
system:resource:tree
system:resource:detail
system:resource:api-ids
system:api:page
system:task:list
```

- [ ] 增加菜单基础接口断言：

```text
system:employee   -> /api/system/employees/page
system:department -> /api/system/departments/tree
system:role       -> /api/system/roles/page
system:resource   -> /api/system/resources/tree
system:api        -> /api/system/apis/page
system:task       -> /api/admin/task/list.htm
```

- [ ] 增加复合操作接口断言：

```text
system:employee:create -> /api/system/employees/create
                          /api/system/departments/tree
system:employee:update -> /api/system/employees/detail
                          /api/system/employees/update
                          /api/system/departments/tree
system:employee:roles  -> /api/system/roles/page
                          /api/system/employees/role-ids
                          /api/system/employees/roles
system:role:update      -> /api/system/roles/detail
                          /api/system/roles/update
system:role:resources   -> /api/system/resources/tree
                          /api/system/roles/resource-ids
                          /api/system/roles/resources
system:resource:update  -> /api/system/resources/detail
                          /api/system/resources/update
system:resource:apis    -> /api/system/apis/page
                          /api/system/resources/api-ids
                          /api/system/resources/apis
system:api:detail       -> /api/system/apis/detail
system:task:run         -> /api/admin/task/run.htm
```

- [ ] 增加断言：完整受保护接口集合中的每条路径至少出现在一个资源接口映射中。
- [ ] 运行失败测试：

```bash
mvn -pl backend/admin-boot -am -Dtest=SqlInitializationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：测试因 `2026080301-system-data-reinit.sql` 尚不存在而失败，失败原因明确指向缺少新初始化文件。

## 任务二：编写完整空库初始化 SQL

- [ ] 新建 `backend/sql/2026080301-system-data-reinit.sql`，文件开头明确执行前提和顺序：

```sql
-- 仅用于空库完成建表后的完整基础数据初始化。
-- 执行顺序：2026072201-system-init.sql -> 2026073101-permission-cache-redesign.sql
--          -> 2026080301-system-data-reinit.sql。
-- 使用本文件时不要再执行 2026073001-system-data-init.sql 和
-- 2026073102-task-management.sql。
SET @init_now = NOW(3);
```

- [ ] 沿用现有幂等方式初始化总部和默认超级管理员；只保存现有 BCrypt 哈希，不写明文密码。
- [ ] 创建 `tmp_system_api_definition` 并登记全部 36 个受保护接口，接口路径以 Controller 路由为准。
- [ ] 创建 `tmp_system_resource_definition`，只保留以下资源树：

```text
system (DIRECTORY)
├── system:employee (MENU)
│   ├── create
│   ├── update
│   ├── status
│   ├── delete
│   ├── reset-password
│   └── roles
├── system:department (MENU)
│   ├── create
│   ├── update
│   ├── status
│   └── delete
├── system:role (MENU)
│   ├── create
│   ├── update
│   ├── status
│   ├── delete
│   └── resources
├── system:resource (MENU)
│   ├── create
│   ├── update
│   ├── status
│   ├── delete
│   └── apis
├── system:api (MENU)
│   ├── detail
│   └── status
└── system:task (MENU，隐藏)
    └── run
```

- [ ] 创建 `tmp_resource_api_definition`：

```sql
CREATE TEMPORARY TABLE tmp_resource_api_definition (
    resource_code VARCHAR(100) NOT NULL,
    api_path VARCHAR(255) NOT NULL,
    PRIMARY KEY (resource_code, api_path)
);
```

- [ ] 按任务一的菜单、复合操作映射，以及各单接口操作，完整写入资源接口定义。
- [ ] 使用临时定义表幂等插入 `t_system_api`、`t_system_resource` 和 `t_resource_api`。
- [ ] 任务数据直接并入本文件，不依赖 `2026073102-task-management.sql`。
- [ ] 初始化结束后按实际插入数量单调推进 `permission_snapshot` 持久版本；重复执行没有新增数据时不推进版本。
- [ ] 删除三张临时表。
- [ ] 重新运行任务一测试，预期全部通过。

## 任务三：同步长期约束

- [ ] 在 `AGENTS.md` 的权限规则中增加：菜单必须关联页面基础读取接口；操作必须关联完成操作的全部接口；禁止为分页、树、关联 ID 机械创建独立操作权限。
- [ ] 在 `docs/superpowers/specs/2026-07-21-java-admin-system-design.md` 的系统资源章节同步同一语义。
- [ ] 在长期设计的初始化章节补充新的空库执行顺序，并说明旧数据脚本与新完整基线不能混用。
- [ ] 在 `docs/superpowers/specs/2026-08-03-permission-resource-initialization-design.md` 标记设计已落地，链接新 SQL 和长期设计真相源。
- [ ] 用源码扫描确认旧口径没有继续声称分页、树和关联 ID 是独立按钮权限：

```bash
rg -n "分页权限|树权限|关联 ID|system:employee:page|system:role:resource-ids|system:resource:api-ids" AGENTS.md docs backend/sql/2026080301-system-data-reinit.sql
```

预期：仅出现禁止性说明或测试中的反例，不出现旧初始化定义。

## 任务四：格式化与交付验证

- [ ] 对本次修改的 Java 测试执行 Google Java Format：

```bash
java -jar tools/google-java-format-1.28.0-all-deps.jar --replace \
  backend/admin-boot/src/test/java/com/oa/boot/config/SqlInitializationTest.java
```

- [ ] 运行相关测试：

```bash
mvn -pl backend/admin-boot -am -Dtest=SqlInitializationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：`SqlInitializationTest` 全部通过。

- [ ] 运行后端全量测试：

```bash
mvn test
```

预期：Reactor 全部模块 `SUCCESS`。

- [ ] 运行后端打包：

```bash
mvn package
```

预期：Reactor 全部模块 `SUCCESS`，生成 `admin-boot` JAR。

- [ ] 检查差异：

```bash
git diff --check
git status --short
```

预期：无空白错误；只包含本任务 SQL、测试和文档，以及上一轮尚未提交的前端回显修复。

- [ ] 明确交付边界：不执行真实 MySQL 初始化，不声称数据库验收完成；向用户提供三份 SQL 的人工执行顺序和旧 SQL 排除清单。
