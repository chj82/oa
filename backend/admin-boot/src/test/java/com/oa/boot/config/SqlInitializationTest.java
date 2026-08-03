package com.oa.boot.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class SqlInitializationTest {
  private static final List<String> PROTECTED_API_PATHS =
      List.of(
          "/api/system/employees/page",
          "/api/system/employees/detail",
          "/api/system/employees/role-ids",
          "/api/system/employees/create",
          "/api/system/employees/update",
          "/api/system/employees/status",
          "/api/system/employees/delete",
          "/api/system/employees/reset-password",
          "/api/system/employees/roles",
          "/api/system/departments/tree",
          "/api/system/departments/detail",
          "/api/system/departments/create",
          "/api/system/departments/update",
          "/api/system/departments/status",
          "/api/system/departments/delete",
          "/api/system/roles/page",
          "/api/system/roles/detail",
          "/api/system/roles/resource-ids",
          "/api/system/roles/create",
          "/api/system/roles/update",
          "/api/system/roles/status",
          "/api/system/roles/delete",
          "/api/system/roles/resources",
          "/api/system/resources/tree",
          "/api/system/resources/detail",
          "/api/system/resources/create",
          "/api/system/resources/update",
          "/api/system/resources/status",
          "/api/system/resources/delete",
          "/api/system/resources/api-ids",
          "/api/system/resources/apis",
          "/api/system/apis/page",
          "/api/system/apis/detail",
          "/api/system/apis/status");

  @Test
  void 数据SQL包含完整基础数据且不保存管理员明文密码() throws IOException {
    String sql = Files.readString(findSql());

    assertTrue(sql.contains("INSERT INTO t_department"));
    assertTrue(sql.contains("INSERT INTO t_employee"));
    assertTrue(sql.contains("INSERT INTO t_system_api"));
    assertTrue(sql.contains("INSERT INTO t_system_resource"));
    assertTrue(sql.contains("INSERT INTO t_resource_api"));
    String passwordHash = "$2a$10$8jnMcI8Np8unWAA34nqEdOvhik2LW1fzt/nM9.cyti06nJmqf2HQC";
    assertTrue(sql.contains(passwordHash));
    assertTrue(new BCryptPasswordEncoder().matches("12345678", passwordHash));
    assertFalse(sql.contains("12345678"));
    for (String path : PROTECTED_API_PATHS) {
      assertTrue(sql.contains("'" + path + "'"), "数据 SQL 缺少接口路径：" + path);
    }
  }

  /** 权限缓存重构SQL必须创建并初始化系统版本。 */
  @Test
  void 权限缓存重构SQL包含快照版本表和初始数据() throws IOException {
    String sql = Files.readString(findSql("2026073101-permission-cache-redesign.sql"));

    assertTrue(sql.contains("CREATE TABLE t_system_version"));
    assertTrue(sql.contains("version_code VARCHAR(64) NOT NULL"));
    assertTrue(sql.contains("version_value BIGINT UNSIGNED NOT NULL"));
    assertTrue(sql.contains("UNIQUE KEY udx_system_version_code (version_code)"));
    assertTrue(sql.contains("created_at DATETIME(3) NOT NULL"));
    assertTrue(sql.contains("updated_at DATETIME(3) NOT NULL"));
    assertTrue(sql.contains("VALUES ('permission_snapshot', 0, NOW(3), NOW(3))"));
  }

  /** 任务管理SQL必须注册列表和手动运行接口并推进权限快照版本。 */
  @Test
  void 任务管理SQL包含接口资源和版本推进() throws IOException {
    String sql = Files.readString(findSql("2026073102-task-management.sql"));

    assertTrue(sql.contains("'/api/admin/task/list.htm'"));
    assertTrue(sql.contains("'/api/admin/task/run.htm'"));
    assertTrue(sql.contains("'system:task'"));
    assertTrue(sql.contains("INSERT INTO t_resource_api"));
    assertTrue(sql.contains("version_value = version_value + 1"));
    assertTrue(sql.contains("version_code = 'permission_snapshot'"));
  }

  private Path findSql() {
    return findSql("2026073001-system-data-init.sql");
  }

  private Path findSql(String filename) {
    for (Path candidate :
        List.of(
            Path.of("sql", filename),
            Path.of("../sql", filename),
            Path.of("backend/sql", filename))) {
      if (Files.exists(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("未找到SQL文件：" + filename);
  }
}
