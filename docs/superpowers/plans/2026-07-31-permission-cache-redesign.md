# 权限缓存重构实施计划

> **面向 Agent 执行者：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务执行；所有步骤使用复选框跟踪。

**目标：** 将当前按员工缓存完整权限并全局失效的实现，重构为员工授权关系 Redis 缓存、角色授权关系 Redis 缓存和 JVM 永久资源权限快照，并以数据库持久版本和 Redis 镜像保证多实例刷新。

**架构：** 鉴权先读取 `admin:employee-auth:<employeeId>`，再通过 `MGET` 批量读取 `admin:role-auth:<roleId>`，最后使用 JVM 中发布后不可变的资源权限快照合并接口集合和资源树。员工与角色缓存使用 10 分钟 Cache Aside；资源快照不设 TTL，通过 `t_system_version` 中的 `permission_snapshot` 版本驱动单线程后台重建，Redis 保存多实例共享镜像，刷新期间请求继续使用旧快照。

**技术栈：** Java 17、Spring Boot 3、Spring Data Redis、MyBatis-Plus、MySQL 8、JUnit 5、Mockito、Google Java Format 1.28.0。

---

## 文档信息

- 文档类型：Plan
- 文档状态：草稿
- 业务分类：系统权限
- 业务主题：权限缓存重构
- 更新时间：2026-07-31
- 上游依据：[Java 基础后台管理系统设计](../specs/2026-07-21-java-admin-system-design.md)
- 完成后吸收目标：上游设计文档、`AGENTS.md`、人工 SQL 和代码测试

## 1. 范围与成功标准

### 纳入范围

- 删除旧 `admin:permission:<employeeId>`、全局版本、pending/lease 和员工重建锁协议。
- 新增员工授权关系和角色授权关系 Redis 缓存，TTL 固定为 10 分钟。
- 新增数据库资源快照持久版本、Redis 单调版本镜像和 30 秒校准。
- 新增 JVM 永久资源权限快照、非阻塞单线程后台重建和发布前二次验版。
- 修改员工、角色、资源和接口写服务的失效动作。
- 迁移现有权限单元测试、结构测试和 SQL 静态测试。

### 不纳入范围

- 不修改 Controller 路径、HTTP 契约、前端菜单协议和登录 Cookie 协议。
- 不引入 Caffeine、消息队列、Redis Pub/Sub、分布式锁或 Redis Cluster。
- 不缓存最终员工接口集合和资源树。
- 不修改已执行的 `2026072201-system-init.sql` 和 `2026073001-system-data-init.sql`。

### 成功标准

- 权限热路径固定为一次员工缓存读取、一次角色批量读取和一次 Redis 版本读取，不查询资源权限基础表。
- 多个角色缓存同时缺失时按集合批量查询，不产生按角色 N+1。
- 单个员工或角色授权变化不再使其他员工缓存失效。
- 资源快照版本变化时每个实例只提交一个后台重建任务，所有请求立即继续使用旧快照。
- Redis 版本同步失败或重启后，30 秒校准可以从数据库恢复且版本不倒退。
- `mvn test` 和 `mvn package` 通过；本次修改的全部 Java 文件已单独执行 Google Java Format。

## 2. 文件结构

### 新增文件

- `backend/sql/2026073101-permission-cache-redesign.sql`：创建并初始化资源快照版本表。
- `backend/admin-entity/src/main/java/com/oa/entity/system/SystemVersionEntity.java`：映射系统版本记录。
- `backend/admin-dao/src/main/java/com/oa/dao/system/SystemVersionMapper.java`：按版本编码读取和原子递增系统版本。
- `backend/admin-common/src/main/java/com/oa/common/model/system/cache/EmployeeAuthorizationCache.java`：员工状态、超级管理员标记和角色 ID 集合。
- `backend/admin-common/src/main/java/com/oa/common/model/system/cache/RoleAuthorizationCache.java`：角色状态和资源 ID 集合。
- `backend/admin-common/src/main/java/com/oa/common/model/system/cache/ResourcePermissionSnapshot.java`：发布到 JVM 的版本化资源快照。
- `backend/admin-common/src/main/java/com/oa/common/model/system/cache/ResourcePermissionNode.java`：快照中的资源节点和接口路径。
- `backend/admin-common/src/main/java/com/oa/common/model/system/permission/ResolvedEmployeePermission.java`：请求内合成结果，不写入 Redis。
- `backend/admin-service/src/main/java/com/oa/service/system/store/StringRedisAuthorizationStore.java`：员工、角色缓存及快照版本镜像访问。
- `backend/admin-service/src/main/java/com/oa/service/system/PermissionSnapshotLoader.java`：从同一数据库一致性视图加载快照。
- `backend/admin-service/src/main/java/com/oa/service/system/PermissionSnapshotService.java`：版本校验、非阻塞后台重建、原子发布和定时校准。
- `backend/admin-boot/src/main/java/com/oa/boot/config/PermissionSnapshotInitializer.java`：在服务进入可用状态前同步完成首次快照加载和版本核对。
- `backend/admin-service/src/main/java/com/oa/service/system/AuthorizationRelationService.java`：Cache Aside 加载员工和角色授权关系及提交后精准删除。
- 对应测试：`SystemVersionMapperTest`、`StringRedisAuthorizationStoreTest`、`PermissionSnapshotLoaderTest`、`PermissionSnapshotServiceTest`、`AuthorizationRelationServiceTest`。

### 修改文件

- `PermissionService.java`：移除全局失效协议，按员工、角色和快照实时合成。
- `EmployeeMapper.java`、`EmployeeRoleMapper.java`、`RoleMapper.java`、`RoleResourceMapper.java`、`ResourceApiMapper.java`、`SystemApiMapper.java`：补充语义化批量查询。
- `EmployeeService.java`、`RoleService.java`：事务提交后精准删除员工或角色缓存。
- `ResourceService.java`、`SystemApiService.java`：快照内容实际变化时在事务内递增持久版本。
- `RedisCacheJsonCodecTest.java`、`AuthenticationStructureTest.java`、`PermissionQueryStructureTest.java` 及四个写 Service 测试：迁移旧断言。
- `README.md`：在人工 SQL 顺序中增加新脚本。

### 删除文件

- `backend/admin-common/src/main/java/com/oa/common/model/system/cache/EmployeePermissionCache.java`
- `backend/admin-service/src/main/java/com/oa/service/system/store/StringRedisPermissionStore.java`
- `backend/admin-service/src/test/java/com/oa/service/system/store/StringRedisPermissionStoreTest.java`

## 3. 实施任务

### 任务 1：持久化资源快照版本

**文件：**

- 新增：`backend/sql/2026073101-permission-cache-redesign.sql`
- 新增：`backend/admin-entity/src/main/java/com/oa/entity/system/SystemVersionEntity.java`
- 新增：`backend/admin-dao/src/main/java/com/oa/dao/system/SystemVersionMapper.java`
- 修改：`backend/admin-boot/src/test/java/com/oa/boot/config/SqlInitializationTest.java`
- 修改：`backend/admin-entity/src/test/java/com/oa/entity/system/EntityMappingTest.java`
- 新增：`backend/admin-service/src/test/java/com/oa/service/system/SystemVersionMapperTest.java`

- [ ] **步骤 1：先写失败的 SQL、Entity 和 Mapper 契约测试**

  SQL 静态测试必须断言新脚本包含 `t_system_version`、`version_code VARCHAR(64) NOT NULL`、`version_value BIGINT UNSIGNED NOT NULL`、唯一版本编码、`DATETIME(3)` 和 `permission_snapshot` 初始版本 `0`。Entity 映射测试断言表名正确，Mapper 测试定义按版本编码原子递增行为。

  ```java
  assertEquals(0L, mapper.selectCurrentVersion());
  assertEquals(1L, mapper.incrementAndSelectVersion());
  assertEquals(2L, mapper.incrementAndSelectVersion());
  ```

- [ ] **步骤 2：运行测试并确认因类型、脚本和 Mapper 不存在而失败**

  ```bash
  mvn -pl backend/admin-boot,backend/admin-entity,backend/admin-dao -am \
    -Dtest=SqlInitializationTest,EntityMappingTest,SystemVersionMapperTest test
  ```

  预期：测试编译或断言失败，原因明确指向新增契约尚未实现。

- [ ] **步骤 3：新增人工 SQL**

  ```sql
  CREATE TABLE t_system_version (
      id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '系统版本ID',
      version_code VARCHAR(64) NOT NULL COMMENT '版本编码',
      version_value BIGINT UNSIGNED NOT NULL COMMENT '版本值',
      created_at DATETIME(3) NOT NULL COMMENT '创建时间',
      updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
      PRIMARY KEY (id),
      UNIQUE KEY udx_system_version_code (version_code)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统版本';

  INSERT INTO t_system_version (
      version_code, version_value, created_at, updated_at
  ) VALUES ('permission_snapshot', 0, NOW(3), NOW(3));
  ```

- [ ] **步骤 4：实现版本 Entity 和 Mapper**

  Entity 使用传统 Java Bean，字段为 `long id`、`String versionCode`、`long versionValue`、`LocalDateTime createdAt`、`LocalDateTime updatedAt`。Mapper 公开：

  ```java
  long selectVersion(@Param("versionCode") String versionCode);

  int incrementVersion(
      @Param("versionCode") String versionCode,
      @Param("updatedAt") LocalDateTime updatedAt);

  default long incrementAndSelectVersion(String versionCode) {
    if (incrementVersion(versionCode, LocalDateTime.now()) != 1) {
      throw new IllegalStateException("系统版本不存在，编码=" + versionCode);
    }
    return selectVersion(versionCode);
  }
  ```

  `incrementVersion` 使用单表 `version_value = version_value + 1`，按唯一版本编码更新，不使用先读后写。

- [ ] **步骤 5：格式化并运行任务测试**

  ```bash
  java -jar tools/google-java-format-1.28.0-all-deps.jar --replace \
    backend/admin-entity/src/main/java/com/oa/entity/system/SystemVersionEntity.java \
    backend/admin-dao/src/main/java/com/oa/dao/system/SystemVersionMapper.java \
    backend/admin-boot/src/test/java/com/oa/boot/config/SqlInitializationTest.java \
    backend/admin-entity/src/test/java/com/oa/entity/system/EntityMappingTest.java \
    backend/admin-service/src/test/java/com/oa/service/system/SystemVersionMapperTest.java
  mvn -pl backend/admin-boot,backend/admin-entity,backend/admin-dao -am \
    -Dtest=SqlInitializationTest,EntityMappingTest,SystemVersionMapperTest test
  ```

  预期：相关测试全部通过。

### 任务 2：员工与角色 Redis 缓存模型和 Store

**文件：**

- 新增：`EmployeeAuthorizationCache.java`、`RoleAuthorizationCache.java`
- 新增：`StringRedisAuthorizationStore.java`
- 新增：`StringRedisAuthorizationStoreTest.java`
- 修改：`RedisCacheJsonCodecTest.java`

- [ ] **步骤 1：写缓存 JSON 和 Redis 行为失败测试**

  缓存对象字段固定为：

  ```java
  EmployeeAuthorizationCache: employeeId, status, superuser, roleIds
  RoleAuthorizationCache: roleId, status, resourceIds
  ```

  测试必须覆盖：员工 `GET/SET/DEL`、角色 `MGET` 保持输入 ID 对应关系、只回填缺失角色、TTL 为 `Duration.ofMinutes(10)`、损坏 JSON 转为 `AuthenticationInfrastructureException`、Redis 异常返回认证基础设施异常。

- [ ] **步骤 2：运行 Store 测试并确认失败**

  ```bash
  mvn -pl backend/admin-service -am \
    -Dtest=StringRedisAuthorizationStoreTest,RedisCacheJsonCodecTest test
  ```

  预期：新增类型和 Store 不存在导致失败。

- [ ] **步骤 3：实现缓存 Bean 和 Store API**

  ```java
  EmployeeAuthorizationCache getEmployee(long employeeId);
  void putEmployee(EmployeeAuthorizationCache cache);
  void deleteEmployee(long employeeId);

  Map<Long, RoleAuthorizationCache> multiGetRoles(Collection<Long> roleIds);
  void putRoles(Collection<RoleAuthorizationCache> caches);
  void deleteRole(long roleId);

  Long getSnapshotVersion();
  long advanceSnapshotVersion(long candidateVersion);
  ```

  Key 固定为 `admin:employee-auth:`、`admin:role-auth:` 和 `admin:permission:snapshot:version`。`advanceSnapshotVersion` 只允许单键 Lua 执行“键不存在或候选版本更大才 SET”，不得保留旧 Store 的跨键脚本。

- [ ] **步骤 4：格式化并运行 Store 测试**

  对任务 2 修改的全部 Java 文件执行 Google Java Format，再运行步骤 2 命令。预期全部通过。

### 任务 3：资源权限快照模型与一致性加载器

**文件：**

- 新增：`ResourcePermissionNode.java`、`ResourcePermissionSnapshot.java`
- 新增：`PermissionSnapshotLoader.java`、`PermissionSnapshotLoaderTest.java`
- 修改：`ResourceApiMapper.java`、`SystemApiMapper.java`

- [ ] **步骤 1：写失败的快照加载测试**

  测试数据必须包含启用目录、禁用目录、菜单、操作、资源接口关联和禁用接口，断言：

  ```java
  assertEquals(8L, snapshot.getVersion());
  assertTrue(snapshot.getNodes().containsKey(100L));
  assertEquals(Set.of("/api/system/employee/page"),
      snapshot.getNodes().get(101L).getApiPaths());
  assertFalse(snapshot.getAllEnabledApiPaths().contains("/api/disabled"));
  ```

  还要验证父级禁用时子节点不进入有效树，以及快照集合包装为不可修改集合。

- [ ] **步骤 2：运行测试并确认加载器不存在而失败**

  ```bash
  mvn -pl backend/admin-service -am -Dtest=PermissionSnapshotLoaderTest test
  ```

- [ ] **步骤 3：补充批量快照 Mapper 方法**

  ```java
  ResourceApiMapper.selectAllRelations();
  SystemApiMapper.selectAllEnabled();
  ```

  两个方法都只访问单表。`PermissionSnapshotLoader.load()` 使用只读事务的一致性视图，依次读取持久版本、全部资源、有序资源接口关系和全部启用接口，并构造发布后不再修改的快照。

- [ ] **步骤 4：实现资源节点和快照 Bean**

  `ResourcePermissionNode` 保存资源展示字段、状态、父 ID 和关联启用接口路径；`ResourcePermissionSnapshot` 保存版本、按 ID 索引的节点和超级管理员全部启用接口路径。构建结束时所有 `List`、`Set`、`Map` 使用 `copyOf` 或等价防御性复制，返回资源树时必须新建 `ResourceVO`，不得把快照内部集合暴露给 Controller。

- [ ] **步骤 5：格式化并运行快照加载测试**

  格式化任务 3 全部 Java 文件，运行步骤 2 命令。预期全部通过。

### 任务 4：快照版本协调、非阻塞后台重建和定时校准

**文件：**

- 新增：`PermissionSnapshotService.java`、`PermissionSnapshotServiceTest.java`
- 新增：`backend/admin-boot/src/main/java/com/oa/boot/config/PermissionSnapshotInitializer.java`
- 新增：`backend/admin-boot/src/test/java/com/oa/boot/config/PermissionSnapshotInitializerTest.java`
- 修改：`backend/admin-boot/src/main/java/com/oa/boot/OaApplication.java`
- 修改：`backend/admin-boot/src/test/java/com/oa/boot/OaApplicationTest.java`

- [ ] **步骤 1：写失败的版本协调测试**

  覆盖以下序列：启动初始化同步加载并发布快照；数据库或 Redis 异常导致初始化失败；初始化加载后版本变化会有限重试；本地版本命中不调用 Loader；本地版本落后只提交一次后台任务；多个并发请求均立即返回同一个旧快照且不等待 Loader；后台加载成功后原子切换；加载后 Redis 版本再次变化时丢弃结果并保留旧快照；后台加载异常时保留旧快照并允许后续请求重试；运行期快照意外为空返回 `AuthenticationInfrastructureException` 且不在请求线程加载；Redis 版本缺失时用数据库版本初始化；候选旧版本不能覆盖 Redis 新版本。

- [ ] **步骤 2：运行测试并确认服务不存在而失败**

  ```bash
  mvn -pl backend/admin-service,backend/admin-boot -am \
    -Dtest=PermissionSnapshotServiceTest,PermissionSnapshotInitializerTest,OaApplicationTest test
  ```

- [ ] **步骤 3：实现协调服务**

  核心状态和入口固定为：

  ```java
  private final AtomicReference<ResourcePermissionSnapshot> current = new AtomicReference<>();
  private final AtomicBoolean refreshInProgress = new AtomicBoolean();
  private final ExecutorService refreshExecutor = Executors.newSingleThreadExecutor();

  public ResourcePermissionSnapshot currentSnapshot();

  public void initializeSnapshot();

  public void refreshIfPersistentVersionChanged();

  public long incrementPersistentVersion();
  ```

  `initializeSnapshot()` 在启动线程同步加载一致性快照、推进 Redis 镜像并再次核对数据库版本，版本变化最多重试三次，失败抛出异常阻止启动。`currentSnapshot()` 只读取本地快照。`refreshIfPersistentVersionChanged()` 比较数据库与本地版本；版本不一致时推进 Redis，并通过 `refreshInProgress.compareAndSet(false, true)` 最多提交一个后台任务。后台加载完成后重新读取数据库版本，只有版本一致才替换 `current`，并在 `finally` 中无条件清除刷新标记。后台加载失败只记录错误，不清空旧快照；运行期 `current` 为空直接抛出认证基础设施异常，不在请求线程调用 Loader。`incrementPersistentVersion()` 只操作数据库版本记录，并注册 `afterCommit` 将新版本以单调方式推进到 Redis及触发本机刷新；无事务时立即同步。单线程执行器必须命名线程，并通过 `@PreDestroy` 调用 `shutdownNow()`，测试结束不得遗留非守护线程。

- [ ] **步骤 4：增加启动门禁、启用调度并限制职责**

  在启动模块新增实现 `ApplicationRunner` 的 `PermissionSnapshotInitializer`，其 `run` 方法只调用 `permissionSnapshotService.initializeSnapshot()`；Runner 返回前应用不得进入可用状态，异常直接导致启动失败。在启动模块启用 Spring Scheduling。定时入口放入独立的 `com.oa.service.task` 框架，只保留 `PermissionSnapshotRefreshTask`：每 5 秒读取数据库持久版本并与本地快照比较，不一致时推进 Redis 镜像并提交单线程后台刷新。权限请求只读取本地快照。任务继承 `AbstractTask`，定时和 `/api/admin/task/run.htm` 手动触发共用 `Task.run()`。

- [ ] **步骤 5：格式化并运行协调测试**

  格式化任务 4 全部 Java 文件，运行步骤 2 命令。预期全部通过。

### 任务 5：Cache Aside 授权关系服务

**文件：**

- 新增：`AuthorizationRelationService.java`、`AuthorizationRelationServiceTest.java`
- 修改：`RoleMapper.java`、`RoleResourceMapper.java`

- [ ] **步骤 1：写失败的员工和角色加载测试**

  测试员工缓存命中不访问 Mapper；员工未命中时单表读取员工和角色 ID 并回填；角色 `MGET` 全命中不访问 Mapper；三个角色中两个缺失时只调用一次角色集合查询和一次角色资源集合查询；返回结果按角色拆分且禁用角色保留状态、不参与后续授权。

- [ ] **步骤 2：运行测试并确认服务不存在而失败**

  ```bash
  mvn -pl backend/admin-service -am -Dtest=AuthorizationRelationServiceTest test
  ```

- [ ] **步骤 3：实现批量 Mapper 契约和关系服务**

  ```java
  EmployeeAuthorizationCache currentEmployee(long employeeId);
  Map<Long, RoleAuthorizationCache> currentRoles(Collection<Long> roleIds);
  void evictEmployeeAfterCommit(long employeeId);
  void evictRoleAfterCommit(long roleId);
  ```

  新增 `RoleMapper.selectExistingByIds(Collection<Long>)`，并让 `RoleResourceMapper` 返回带 `roleId/resourceId` 的单表关联记录，以便一次查询后按角色分组。每批最多 500 个角色 ID；禁止循环调用 `selectResourceIdsByRoleId`。

- [ ] **步骤 4：实现提交后删除**

  `evictEmployeeAfterCommit` 和 `evictRoleAfterCommit` 在事务同步可用时仅注册 `afterCommit`，回滚不删除；无事务时立即删除。删除 Redis 失败不得回滚已经提交的数据库事务，但必须记录包含对象 ID 且不包含敏感信息的错误日志，依靠 10 分钟 TTL 收敛。

- [ ] **步骤 5：格式化并运行关系服务测试**

  格式化任务 5 全部 Java 文件，运行步骤 2 命令。预期全部通过。

### 任务 6：重写权限合成热路径

**文件：**

- 新增：`ResolvedEmployeePermission.java`
- 修改：`PermissionService.java`
- 重写：`PermissionServiceTest.java`
- 修改：`PermissionQueryStructureTest.java`

- [ ] **步骤 1：用新行为重写失败测试**

  覆盖：禁用或不存在员工返回空权限；超级管理员使用快照全部有效资源和接口且不读取角色；普通员工读取员工角色后一次批量读取角色缓存；禁用角色被忽略；多角色资源取并集；资源父链无效时子资源不授权；接口路径取并集；`getResources` 返回独立资源树，调用方修改结果不污染快照。

- [ ] **步骤 2：运行测试并确认旧实现不满足新依赖和查询约束**

  ```bash
  mvn -pl backend/admin-service -am \
    -Dtest=PermissionServiceTest,PermissionQueryStructureTest test
  ```

- [ ] **步骤 3：实现请求内合成**

  `PermissionService` 只依赖 `AuthorizationRelationService` 和 `PermissionSnapshotService`。保留公开方法：

  ```java
  public boolean hasPermission(long employeeId, String apiPath);
  public List<ResourceVO> getResources(long employeeId);
  ```

  私有合成方法返回 `ResolvedEmployeePermission`，不写 Redis。普通员工对启用角色的资源 ID 取并集，再根据快照验证资源及完整父链有效性，补齐已授权节点的有效祖先并生成资源树；接口集合只来自最终有效资源。超级管理员直接使用快照预计算结果。

- [ ] **步骤 4：删除 PermissionService 中的旧协议**

  删除 `invalidateAll()`、`retryPendingInvalidation()`、员工重建锁等待、全局版本读写、`EmployeePermissionCache` 和七个 Mapper 直接依赖。结构测试明确禁止 `PermissionService` 引用 Mapper、`StringRedisPermissionStore` 和事务同步 API。

- [ ] **步骤 5：格式化并运行权限测试**

  格式化任务 6 全部 Java 文件，运行步骤 2 命令。预期全部通过。

### 任务 7：迁移员工与角色写操作的精准失效

**文件：**

- 修改：`EmployeeService.java`、`EmployeeServiceTest.java`
- 修改：`RoleService.java`、`RoleServiceTest.java`

- [ ] **步骤 1：先把旧 `invalidateAll()` 断言改为精准删除失败测试**

  员工状态、超级管理员标记、删除和员工角色保存实际变化时，断言调用 `evictEmployeeAfterCommit(employeeId)`；纯姓名、手机号等修改不删除授权缓存。角色状态、删除和角色资源集合实际变化时，断言调用 `evictRoleAfterCommit(roleId)`；角色名称、编码、描述变化不删除。

- [ ] **步骤 2：运行两类 Service 测试并确认旧实现失败**

  ```bash
  mvn -pl backend/admin-service -am \
    -Dtest=EmployeeServiceTest,RoleServiceTest test
  ```

- [ ] **步骤 3：替换依赖和调用位置**

  两个 Service 改为依赖 `AuthorizationRelationService`。删除动作必须在数据库校验和实际写入成功的事务路径注册；无变化、并发更新失败或事务回滚不得删除缓存。员工禁用、删除、密码重置和超级管理员标记变化继续保留会话失效逻辑。

- [ ] **步骤 4：格式化并运行任务测试**

  格式化四个 Java 文件并运行步骤 2 命令。预期全部通过。

### 任务 8：迁移资源与接口写操作的快照版本递增

**文件：**

- 修改：`ResourceService.java`、`ResourceServiceTest.java`
- 修改：`SystemApiService.java`、`SystemApiServiceTest.java`

- [ ] **步骤 1：先写版本递增失败测试**

  资源新增、删除、任何展示或权限字段实际修改、状态变化、父级变化、类型变化及资源接口集合变化，都断言同一事务内调用一次 `incrementPersistentVersion()`。无变化、锁后二次读取发现并发变化或事务回滚路径不推进 Redis 镜像。接口状态实际变化时递增版本，无变化不递增。

- [ ] **步骤 2：运行测试并确认旧全局失效实现失败**

  ```bash
  mvn -pl backend/admin-service -am \
    -Dtest=ResourceServiceTest,SystemApiServiceTest test
  ```

- [ ] **步骤 3：替换写服务依赖**

  两个 Service 改为依赖 `PermissionSnapshotService`。版本递增必须发生在数据库事务内并位于实际写入成功路径，不得在获取数据库行锁之前访问 Redis；Redis 镜像只由事务提交回调推进。资源接口请求集合与锁后二次读取集合均未变化时不得递增版本。

- [ ] **步骤 4：格式化并运行任务测试**

  格式化四个 Java 文件并运行步骤 2 命令。预期全部通过。

### 任务 9：删除旧协议并完成结构与装配回归

**文件：**

- 删除：`EmployeePermissionCache.java`、`StringRedisPermissionStore.java`、`StringRedisPermissionStoreTest.java`
- 修改：`RedisCacheJsonCodecTest.java`
- 修改：`AuthenticationStructureTest.java`
- 修改：`AuthenticationAssemblyTest.java`
- 修改：`README.md`

- [ ] **步骤 1：写旧协议不存在的结构断言**

  扫描 `backend` 源码并断言不再包含：

  ```text
  admin:permission:<employeeId>
  admin:permission:version
  admin:permission:invalidating:pending
  admin:permission:invalidating:lease:
  admin:permission:rebuild-lock:
  invalidateAll(
  EmployeePermissionCache
  StringRedisPermissionStore
  ```

- [ ] **步骤 2：删除旧文件并修正 Spring 装配测试**

  删除旧类和旧测试，确保新 Store、关系服务、快照 Loader、快照服务和调度能力各只有一个 Bean。README 的人工 SQL顺序追加：

  ```bash
  mysql -u root -p oa < backend/sql/2026073101-permission-cache-redesign.sql
  ```

- [ ] **步骤 3：格式化本任务修改的 Java 文件并运行结构测试**

  ```bash
  mvn -pl backend/admin-service,backend/admin-boot -am \
    -Dtest=AuthenticationStructureTest,AuthenticationAssemblyTest,RedisCacheJsonCodecTest test
  ```

  预期：结构扫描、Bean 装配和新缓存 JSON 往返测试全部通过。

### 任务 10：全量验证和文档收口

**文件：**

- 核对：`AGENTS.md`
- 核对：`docs/superpowers/specs/2026-07-21-java-admin-system-design.md`
- 更新：本计划状态

- [ ] **步骤 1：确认全部本次 Java 文件已格式化**

  通过 `git diff --name-only -- '*.java'` 获取精确文件列表，对列表执行：

  ```bash
  java -jar tools/google-java-format-1.28.0-all-deps.jar --replace <本次修改的全部Java文件>
  ```

- [ ] **步骤 2：运行缓存与权限相关测试集合**

  ```bash
  mvn -pl backend/admin-service,backend/admin-boot -am \
    -Dtest='*Permission*,*Authorization*,EmployeeServiceTest,RoleServiceTest,ResourceServiceTest,SystemApiServiceTest,AuthenticationAssemblyTest' test
  ```

  预期：测试退出码为 0，无失败和错误。

- [ ] **步骤 3：运行后端最小验证**

  ```bash
  mvn test
  ```

  预期：全部模块测试通过。

- [ ] **步骤 4：运行后端交付验证**

  ```bash
  mvn package
  ```

  预期：构建成功且所有模块产物生成。

- [ ] **步骤 5：运行静态和文档检查**

  ```bash
  git diff --check
  rg -n "admin:permission:version|invalidating|rebuild-lock|EmployeePermissionCache|StringRedisPermissionStore" \
    AGENTS.md docs backend --glob '!**/target/**'
  python3 /Users/admin/.codex/plugins/cache/jiaxiao-team/mc-engineering/0.2.0/skills/document-blueprint/scripts/check_doc_links.py \
    --repo-root /Users/admin/Documents/workspace/oa
  ```

  预期：`git diff --check` 和链接检查通过；`rg` 只允许本计划中作为删除目标的文字出现，不得在代码、设计真相源或 `AGENTS.md` 中出现。

- [ ] **步骤 6：记录未执行的环境验证**

  如未实际连接 MySQL、Redis 或启动两个应用实例，交付说明必须明确以下项目未完成：人工执行新 SQL、真实 Redis TTL/MGET、Redis 重启恢复、双实例版本刷新和浏览器端菜单验收。

## 4. 阶段门禁

### 门禁 A：数据和缓存基础设施

- 任务 1～5 完成。
- 新版本表、缓存对象、Store、快照 Loader 和协调器的聚焦测试通过。
- 未开始改写业务 Service 前，新基础设施可以独立编译和测试。

### 门禁 B：权限热路径

- 任务 6 完成。
- 权限热路径不直接依赖任何 Mapper，不再写最终员工权限缓存。
- 超级管理员、多角色合并、父链失效和资源树隔离测试通过。

### 门禁 C：写路径和旧协议清理

- 任务 7～9 完成。
- 员工/角色精准删除与资源快照版本递增边界正确。
- 旧 Key、旧类、旧 Lua 和 `invalidateAll()` 从运行代码中消失。

### 门禁 D：交付验证

- `mvn test`、`mvn package`、差异检查和文档链接检查通过。
- 未执行的真实环境验证已明确记录，不以 Mock 测试冒充。

## 5. 风险与控制

- **缓存删除并发回填：** 接受最多 10 分钟旧员工或角色关系；测试明确该窗口，不加入延迟双删。
- **版本镜像倒退：** Redis 只通过单键单调推进操作写入；事务回调和定时校准均复用同一入口。
- **快照重建风暴：** 每实例使用原子刷新标记和单线程执行器，同一时刻只有一个后台任务；请求线程不等待刷新。
- **快照半成品发布：** 服务启动先完整加载并验版，Loader 完成全部防御性复制后才替换 `AtomicReference`，返回 VO 时再次复制。
- **数据库压力转移：** 角色缺失必须批量查询；快照只在版本变化时全量查询；30 秒校准只读单行版本。
- **版本事务热点：** 仅资源、资源接口和接口目录变化递增单行版本，员工和角色授权变化不访问该版本行。
- **Redis 同步失败：** 数据库版本是真相源，提交后立即同步失败时由 30 秒校准恢复；Redis 不可用期间员工和角色授权缓存无法读取，鉴权返回 `503`，已有快照的后台刷新失败不影响当前请求。
- **迁移期间双协议并存：** 在任务 9 前不得对外宣称重构完成；最终一次性删除旧类和旧 Key 使用点。

## 6. 执行要求

- 严格按任务顺序执行，每个行为变更先确认测试因预期原因失败，再写最小实现。
- 每次修改 Java 文件后，只格式化本次修改的 Java 文件。
- 不修改用户已有无关改动，不顺带重构部门、登录或前端代码。
- 用户未明确要求时不执行 Git commit、push、merge；计划中的阶段门禁替代默认的频繁提交步骤。
- 新 SQL 只新增文件，不修改已经执行的历史 SQL。
- 完成后将本计划状态改为“已完成”，并把真实验证结果写入当前状态，不把执行流水复制进长期设计。

## 7. 当前状态

- 当前状态：草稿
- 当前阶段：等待选择执行方式
- 已完成项：设计文档和项目硬规则已确认；实施任务、门禁和验证路径已拆分。
- 阻塞项：无。
- 关键决策：服务启动完成前必须加载并验版资源快照；运行期接受员工与角色缓存最多 10 分钟最终一致，资源快照由持久版本驱动非阻塞后台刷新，刷新中继续使用旧快照。
- 下一步：选择分任务执行方式后，从任务 1 的失败测试开始。
