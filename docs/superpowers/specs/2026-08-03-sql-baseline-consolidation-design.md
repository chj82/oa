# SQL 基线整理设计

## 1. 背景与目标

项目尚未上线，数据库可以删除后从空库重建，不需要继续维护面向已部署环境的增量 SQL。当前 `backend/sql/` 同时存在结构基线、旧数据初始化、权限快照补丁、任务管理补丁和新数据基线，执行者需要额外判断哪些脚本应执行，README 与最新实现也已经不一致。

本次将数据库初始化收敛为唯一的结构脚本和数据脚本，使空库重建只有一条明确执行路径，同时保持系统接口、权限资源和当前代码一致。

## 2. 最终文件

### 2.1 `backend/sql/schema.sql`

- 包含当前项目全部表、主键、唯一索引和普通索引。
- 将 `t_system_version` 直接纳入完整建表基线。
- 不包含基础数据。
- 不执行 `DROP DATABASE`、`CREATE DATABASE` 或 `USE`，避免误删或误选数据库。
- 仅面向空库执行，不提供旧库升级和历史数据兼容能力。

### 2.2 `backend/sql/data.sql`

- 初始化根部门和默认超级管理员。
- 初始化当前全部受保护接口。
- 初始化系统资源树和资源接口关联。
- 任务管理数据直接包含在完整基线中。
- 初始化 `permission_snapshot` 系统版本。
- 使用事务保证权限数据、资源接口关联和快照版本一致。
- 重复执行不新增重复数据，便于初始化中断后重新执行。

## 3. 删除范围

删除以下已被新基线完整取代的脚本：

- `backend/sql/2026072201-system-init.sql`
- `backend/sql/2026073001-system-data-init.sql`
- `backend/sql/2026073101-permission-cache-redesign.sql`
- `backend/sql/2026073102-task-management.sql`
- `backend/sql/2026080301-system-data-reinit.sql`

项目尚未上线，因此不保留这些脚本作为历史迁移入口。Git 历史仍可追溯原始内容。

## 4. 空库初始化流程

数据库本身由人工创建：

```sql
DROP DATABASE IF EXISTS oa;
CREATE DATABASE oa CHARACTER SET utf8mb4;
```

随后在明确选中 `oa` 数据库的前提下依次执行：

```text
backend/sql/schema.sql
backend/sql/data.sql
```

应用启动过程不得执行 SQL、扫描 Controller 或自动修正基础数据。

## 5. 测试与文档同步

- Java SQL 结构测试改为读取 `schema.sql` 和 `data.sql`。
- 测试继续精确校验 36 个受保护接口、资源接口映射、默认管理员哈希和权限快照版本。
- README 只保留两份基线 SQL 的执行命令。
- `AGENTS.md` 和长期设计文档更新为未上线阶段可直接维护完整基线；项目上线后再恢复“已执行脚本不可修改、变更新增迁移 SQL”的规则。
- 历史实施计划保留为交付记录，但明确其中旧 SQL 路径已被 `schema.sql` 和 `data.sql` 取代。

## 6. 验收标准

- `backend/sql/` 只保留 `schema.sql` 和 `data.sql`。
- `schema.sql` 包含当前全部 9 张表及完整索引。
- `data.sql` 包含当前 36 个受保护接口，且每个接口至少关联一个有效资源。
- 菜单包含页面基础查询接口，复合操作包含完成操作所需的查询和写入接口。
- 全库不存在仍要求执行旧日期 SQL 的有效说明或测试引用。
- Google Java Format、`mvn test`、`mvn package` 和 `git diff --check` 全部通过。
- 未实际连接 MySQL 执行脚本时，交付说明明确真实数据库初始化尚未验收。
