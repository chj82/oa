# SQL 基线整理实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将未上线项目的 5 份历史 SQL 收敛为可从空库依次执行的 `schema.sql` 和 `data.sql`。

**Architecture:** `schema.sql` 是全部 9 张表及索引的唯一结构基线，`data.sql` 是部门、管理员、接口、资源、关联和权限快照版本的唯一数据基线。Java 静态测试精确约束文件名称、表结构、36 个受保护接口和资源接口映射，README 与长期设计只保留这一条初始化路径。

**Tech Stack:** MySQL 8、Java 17、JUnit 5、Maven、Google Java Format 1.28.0

---

### Task 1: 将测试契约切换到两份新基线

**Files:**
- Modify: `backend/admin-boot/src/test/java/com/oa/boot/config/SqlInitializationTest.java`
- Modify: `backend/admin-entity/src/test/java/com/oa/entity/system/EntityMappingTest.java`
- Modify: `backend/admin-service/src/test/java/com/oa/service/system/RolePersistenceStructureTest.java`

- [x] **Step 1: 修改 SQL 初始化测试文件名**

将数据测试默认文件改为 `data.sql`，权限快照版本测试改为读取 `schema.sql`。增加断言，确认结构脚本精确包含 9 个 `CREATE TABLE`，并包含 `t_system_version`、版本唯一索引和 `permission_snapshot` 初始数据所需结构。

- [x] **Step 2: 修改 Entity 和持久化结构测试文件名**

把两个测试中所有 `2026072201-system-init.sql` 候选路径改为 `schema.sql`，保留原有字段和索引断言。

- [x] **Step 3: 格式化本次修改的 Java 文件**

Run:

```bash
java -jar tools/google-java-format-1.28.0-all-deps.jar --replace \
  backend/admin-boot/src/test/java/com/oa/boot/config/SqlInitializationTest.java \
  backend/admin-entity/src/test/java/com/oa/entity/system/EntityMappingTest.java \
  backend/admin-service/src/test/java/com/oa/service/system/RolePersistenceStructureTest.java
```

Expected: 命令退出码为 0，只格式化以上三个文件。

- [x] **Step 4: 运行定向测试并确认 RED**

Run:

```bash
mvn -pl backend/admin-boot -am \
  -Dtest=SqlInitializationTest,EntityMappingTest,RolePersistenceStructureTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 因 `schema.sql` 或 `data.sql` 尚不存在而失败，失败原因不是 Java 编译错误。

### Task 2: 建立完整结构和数据基线

**Files:**
- Create: `backend/sql/schema.sql`
- Create: `backend/sql/data.sql`
- Delete: `backend/sql/2026072201-system-init.sql`
- Delete: `backend/sql/2026073001-system-data-init.sql`
- Delete: `backend/sql/2026073101-permission-cache-redesign.sql`
- Delete: `backend/sql/2026073102-task-management.sql`
- Delete: `backend/sql/2026080301-system-data-reinit.sql`

- [x] **Step 1: 生成完整结构基线**

以 `2026072201-system-init.sql` 的 8 张业务表为主体，将 `2026073101-permission-cache-redesign.sql` 中的 `t_system_version` 表合并到末尾。`schema.sql` 不包含数据插入、数据库删除、数据库创建或 `USE`。

- [x] **Step 2: 生成完整数据基线**

以 `2026080301-system-data-reinit.sql` 为主体，将 `permission_snapshot` 初始版本插入合并到事务开头。保留 36 个接口、当前资源树、完整资源接口映射、BCrypt 管理员哈希、幂等插入和事务边界，移除旧文件执行顺序与互斥说明。

- [x] **Step 3: 删除五份旧 SQL**

删除已由两份新基线完整替代的日期脚本，确保 `backend/sql/` 只剩：

```text
backend/sql/schema.sql
backend/sql/data.sql
```

- [x] **Step 4: 运行定向测试并确认 GREEN**

Run:

```bash
mvn -pl backend/admin-boot -am \
  -Dtest=SqlInitializationTest,EntityMappingTest,RolePersistenceStructureTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 相关测试全部通过。

### Task 3: 同步执行说明和长期约束

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-07-21-java-admin-system-design.md`
- Modify: `docs/superpowers/specs/2026-07-30-sql-system-initialization-design.md`
- Modify: `docs/superpowers/specs/2026-08-03-permission-resource-initialization-design.md`
- Modify: `docs/superpowers/plans/2026-07-22-oa-admin-system.md`
- Modify: `docs/superpowers/plans/2026-07-30-sql-system-initialization.md`
- Modify: `docs/superpowers/plans/2026-07-31-permission-cache-redesign.md`
- Modify: `docs/superpowers/plans/2026-08-03-permission-resource-initialization.md`

- [x] **Step 1: 更新当前执行入口**

README 只保留 `schema.sql`、`data.sql` 两条导入命令，并明确数据库由人工创建。AGENTS 和长期设计说明项目未上线时直接维护完整基线，上线后才采用不可修改历史脚本的增量迁移策略。

- [x] **Step 2: 更新专项设计真相源**

SQL 初始化设计与权限资源设计统一指向两份新基线，不再描述三份脚本顺序或排除旧脚本。

- [x] **Step 3: 标记历史计划路径已被替代**

在三份历史实施计划开头增加醒目标记：计划中的日期 SQL 路径仅为历史记录，当前执行入口是 `schema.sql` 和 `data.sql`。不重写历史步骤正文。

- [x] **Step 4: 扫描旧路径引用**

Run:

```bash
rg -n "2026072201-system-init|2026073001-system-data-init|2026073101-permission-cache-redesign|2026073102-task-management|2026080301-system-data-reinit" \
  README.md AGENTS.md backend docs
```

Expected: 只允许出现在历史计划的“已被替代”说明或删除清单中，不存在有效执行命令、测试路径或当前设计引用。

### Task 4: 完整验证与交付检查

**Files:**
- Verify: `backend/sql/schema.sql`
- Verify: `backend/sql/data.sql`
- Verify: 本计划涉及的 Java、Markdown 和 SQL 文件

- [x] **Step 1: 重新格式化修改过的 Java 文件**

Run Task 1 Step 3 的 Google Java Format 命令。

- [x] **Step 2: 运行后端全量测试**

Run:

```bash
mvn test
```

Expected: Reactor 全部模块 `SUCCESS`。

- [x] **Step 3: 运行后端打包**

Run:

```bash
mvn package
```

Expected: Reactor 全部模块 `SUCCESS`。

- [x] **Step 4: 执行静态检查**

Run:

```bash
find backend/sql -maxdepth 1 -type f -name '*.sql' -print | sort
rg -n '^CREATE TABLE' backend/sql/schema.sql
git diff --check
git status --short
```

Expected: SQL 目录只有两份脚本，结构脚本有 9 个表，差异无空白错误，原有前端改动保持不变。

- [x] **Step 5: 明确未验收项**

若本机仍没有可用 MySQL 客户端或连接配置，交付说明必须明确：未实际删除数据库、创建数据库和执行两份 SQL，不声称真实数据库初始化通过。

本机 Oracle JDK 17 禁止 Mockito 默认 self-attach，全量验证实际使用：

```bash
mvn -DargLine=-Djdk.attach.allowAttachSelf=true test
mvn -DargLine=-Djdk.attach.allowAttachSelf=true package
```
