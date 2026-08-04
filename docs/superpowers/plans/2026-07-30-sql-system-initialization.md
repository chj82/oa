# OA 系统 SQL 初始化实施计划

> **历史说明：** 本计划中的日期 SQL 路径已由 `backend/sql/schema.sql` 和 `backend/sql/data.sql` 取代，仅保留正文作为实施记录。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除运行时代码初始化和接口扫描，将系统基础数据完整迁移到人工执行的 SQL。

**Architecture:** 建表 SQL继续只维护结构，新增幂等的数据初始化 SQL维护根部门、默认管理员、接口、资源及关联。Spring Boot 不再读取初始化命令，正常启动时不写入基础数据。

**Tech Stack:** Java 17、Spring Boot 3、MyBatis-Plus、MySQL 8、JUnit 5、Maven

---

### Task 1: 建立 SQL 初始化覆盖测试

**Files:**
- Create: `backend/admin-boot/src/test/java/com/oa/boot/config/SqlInitializationTest.java`
- Create: `backend/sql/2026073001-system-data-init.sql`

- [ ] **Step 1: 编写失败测试**

测试读取数据 SQL，断言包含默认管理员 BCrypt 哈希、五组系统菜单、34 条受控接口路径及对应关联写入，并断言不包含 `12345678` 明文。

- [ ] **Step 2: 验证测试失败**

Run: `mvn -pl admin-boot -am -Dtest=SqlInitializationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL，原因是数据 SQL 尚无完整初始化内容。

- [ ] **Step 3: 编写最小数据 SQL**

使用 `INSERT ... SELECT ... WHERE NOT EXISTS` 按业务唯一键插入根部门、管理员、接口、资源和资源接口关联。所有关联通过单表子查询取得 ID，不依赖固定自增 ID；管理员密码列写入 BCrypt 哈希。

- [ ] **Step 4: 验证测试通过**

Run: `mvn -pl admin-boot -am -Dtest=SqlInitializationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 2: 删除代码初始化与扫描入口

**Files:**
- Delete: `backend/admin-boot/src/main/java/com/oa/boot/command/InitializeSystemRunner.java`
- Delete: `backend/admin-boot/src/main/java/com/oa/boot/command/SyncApisRunner.java`
- Delete: `backend/admin-service/src/main/java/com/oa/service/system/SystemInitializationService.java`
- Delete: `backend/admin-boot/src/test/java/com/oa/boot/command/InitializeSystemRunnerTest.java`
- Delete: `backend/admin-boot/src/test/java/com/oa/boot/command/SyncApisRunnerTest.java`
- Delete: `backend/admin-service/src/test/java/com/oa/service/system/SystemInitializationServiceTest.java`
- Modify: `backend/admin-boot/src/main/resources/application.yml`
- Modify: `backend/admin-boot/src/test/java/com/oa/boot/config/ApplicationConfigurationTest.java`

- [ ] **Step 1: 修改配置测试**

断言 `application.yml` 不再包含 `app.command`、`initial-admin-username`、`initial-admin-password`。

- [ ] **Step 2: 删除初始化类、扫描类及其专项测试**

删除仅服务于启动命令的生产代码和测试，移除 `application.yml` 中整个初始化配置块。

- [ ] **Step 3: 运行配置与上下文测试**

Run: `mvn -pl admin-boot -am -Dtest=ApplicationConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS，Spring 上下文不再注册初始化 Runner。

### Task 3: 清理扫描同步专用能力

**Files:**
- Modify: `backend/admin-service/src/main/java/com/oa/service/system/SystemApiService.java`
- Modify: `backend/admin-service/src/test/java/com/oa/service/system/SystemApiServiceTest.java`
- Delete: `backend/admin-common/src/main/java/com/oa/common/model/system/dto/SystemApiDefinitionDTO.java`
- Modify: `backend/admin-common/src/main/java/com/oa/common/model/common/enums/ExceptionCode.java`

- [ ] **Step 1: 定位仅由扫描链路引用的方法和异常码**

Run: `rg -n "SystemApiDefinitionDTO|SYSTEM_API_.*(MISSING|INVALID)|INITIALIZATION_|sync\\(" backend`

Expected: 仅剩待清理的同步方法、DTO、异常码及测试引用。

- [ ] **Step 2: 删除孤儿能力**

从 `SystemApiService` 删除批量同步方法及只为该方法存在的辅助逻辑；保留分页、详情和状态修改。删除同步专项测试、DTO和无引用异常码。

- [ ] **Step 3: 格式化修改的 Java 文件并运行相关测试**

Run: `java -jar tools/google-java-format-1.28.0-all-deps.jar --replace <本任务修改的Java文件>`

Run: `mvn -pl admin-service -am -Dtest=SystemApiServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: PASS。

### Task 4: 更新项目真相源与交付验证

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-07-21-java-admin-system-design.md`

- [ ] **Step 1: 更新初始化说明**

明确首次部署依次执行结构 SQL和数据 SQL，删除 `APP_COMMAND` 与管理员环境变量说明；新增接口时必须提交 SQL变更。

- [ ] **Step 2: 静态扫描残留**

Run: `rg -n "initialize-system|sync-apis|INITIAL_ADMIN|APP_COMMAND|InitializeSystemRunner|SyncApisRunner|SystemInitializationService" .`

Expected: 仅历史设计记录或本次设计/计划文档中出现；长期真相源和运行代码无旧链路。

- [ ] **Step 3: 全量验证**

Run: `mvn test`

Run: `mvn package`

Expected: 两条命令均为 BUILD SUCCESS。

- [ ] **Step 4: 检查工作区**

Run: `git diff --check && git status --short`

Expected: 无空白错误，只包含本次改造和用户原有的配置修改；不执行 Git 提交。
