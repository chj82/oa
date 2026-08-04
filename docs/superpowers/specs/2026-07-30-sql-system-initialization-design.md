# OA 系统 SQL 初始化设计

## 1. 目标

系统结构和基础数据全部通过人工审核、人工执行的 SQL 初始化。应用启动不扫描 Controller，不创建或修正部门、管理员、系统资源、系统接口及关联数据。

## 2. SQL 职责

- `backend/sql/schema.sql` 负责当前全部表和索引，是项目上线前唯一结构基线。
- `backend/sql/data.sql` 负责写入根部门、默认超级管理员、全部系统接口、系统资源、资源接口关联和权限快照初始版本。
- 数据 SQL 支持重复执行：目标数据已存在且业务键一致时不重复插入，不主动覆盖后台已维护的数据。
- 默认管理员用户名为 `admin`，初始密码为 `12345678`；SQL 仅保存 BCrypt 哈希，不保存明文密码。
- SQL 由开发或运维人员审核后手工执行，应用启动不得自动执行。

## 3. 应用代码调整

- 删除 `InitializeSystemRunner` 和 `SyncApisRunner`，取消 `initialize-system`、`sync-apis` 命令。
- 删除仅供代码初始化使用的 `SystemInitializationService` 及其测试。
- 从 `SystemApiService` 删除仅供扫描同步使用的接口同步能力；保留接口管理页面依赖的查询和状态修改能力。
- 删除仅供扫描同步使用的 DTO、异常码和孤儿测试。
- 删除 `app.command`、`app.initial-admin-username`、`app.initial-admin-password` 配置。

## 4. 后续维护约束

项目上线前新增、删除或修改 Controller 接口路径时，必须同步修改 `backend/sql/data.sql`。首次生产部署后冻结两份基线，后续变更新增迁移 SQL。接口路径、系统资源和资源接口关联以 SQL 为唯一初始化真相源，不再从运行时代码推导。

## 5. 验证

- 静态核对数据 SQL 覆盖全部受权限控制的 Controller 路径。
- 验证数据 SQL 不包含明文管理员密码，并具备重复执行语义。
- 运行 Google Java Format 格式化所有本次修改的 Java 文件。
- 运行 `mvn test` 和 `mvn package`。
- 未实际执行 MySQL 数据初始化时，明确标注未完成真实数据库验收。
