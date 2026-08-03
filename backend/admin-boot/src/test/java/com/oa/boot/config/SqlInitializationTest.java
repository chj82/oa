package com.oa.boot.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
          "/api/system/apis/status",
          "/api/admin/task/list.htm",
          "/api/admin/task/run.htm");

  private static final List<String> REMOVED_TECHNICAL_RESOURCE_CODES =
      List.of(
          "system:employee:page",
          "system:employee:detail",
          "system:employee:role-ids",
          "system:department:tree",
          "system:department:detail",
          "system:role:page",
          "system:role:detail",
          "system:role:resource-ids",
          "system:resource:tree",
          "system:resource:detail",
          "system:resource:api-ids",
          "system:api:page",
          "system:task:list");

  private static final Pattern RESOURCE_API_DEFINITION_PATTERN =
      Pattern.compile(
          "INSERT\\s+INTO\\s+tmp_resource_api_definition\\s*"
              + "\\(\\s*resource_code\\s*,\\s*api_path\\s*\\)\\s*VALUES\\s*(.*?);",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private static final Pattern SYSTEM_API_DEFINITION_PATTERN =
      Pattern.compile(
          "INSERT\\s+INTO\\s+tmp_system_api_definition\\s*" + "\\([^;]+?\\)\\s*VALUES\\s*(.*?);",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  private static final Pattern RESOURCE_API_VALUE_PATTERN =
      Pattern.compile("\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*\\)");

  private static final Pattern SYSTEM_API_VALUE_PATTERN =
      Pattern.compile("\\(\\s*'[^']+'\\s*,\\s*'([^']+)'\\s*,");

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
    assertEquals(Set.copyOf(PROTECTED_API_PATHS), parseSystemApiPaths(sql), "系统接口定义集合不正确");
  }

  /** 完整初始化SQL必须按页面和操作语义关联所需接口。 */
  @Test
  void 数据SQL按页面和操作语义关联接口() throws IOException {
    String sql = Files.readString(findSql());
    Map<String, Set<String>> resourceApiMappings = parseResourceApiMappings(sql);

    for (String resourceCode : REMOVED_TECHNICAL_RESOURCE_CODES) {
      assertFalse(sql.contains("'" + resourceCode + "'"), "数据 SQL 仍包含技术资源：" + resourceCode);
    }

    assertMapping(resourceApiMappings, "system:employee", "/api/system/employees/page");
    assertMapping(resourceApiMappings, "system:department", "/api/system/departments/tree");
    assertMapping(resourceApiMappings, "system:role", "/api/system/roles/page");
    assertMapping(resourceApiMappings, "system:resource", "/api/system/resources/tree");
    assertMapping(resourceApiMappings, "system:api", "/api/system/apis/page");
    assertMapping(resourceApiMappings, "system:task", "/api/admin/task/list.htm");

    assertMapping(
        resourceApiMappings,
        "system:employee:create",
        "/api/system/employees/create",
        "/api/system/departments/tree");
    assertMapping(
        resourceApiMappings,
        "system:employee:update",
        "/api/system/employees/detail",
        "/api/system/employees/update",
        "/api/system/departments/tree");
    assertMapping(
        resourceApiMappings,
        "system:employee:roles",
        "/api/system/roles/page",
        "/api/system/employees/role-ids",
        "/api/system/employees/roles");
    assertMapping(
        resourceApiMappings,
        "system:role:update",
        "/api/system/roles/detail",
        "/api/system/roles/update");
    assertMapping(
        resourceApiMappings,
        "system:role:resources",
        "/api/system/resources/tree",
        "/api/system/roles/resource-ids",
        "/api/system/roles/resources");
    assertMapping(
        resourceApiMappings,
        "system:resource:update",
        "/api/system/resources/detail",
        "/api/system/resources/update");
    assertMapping(
        resourceApiMappings,
        "system:resource:apis",
        "/api/system/apis/page",
        "/api/system/resources/api-ids",
        "/api/system/resources/apis");
    assertMapping(resourceApiMappings, "system:api:detail", "/api/system/apis/detail");
    assertMapping(resourceApiMappings, "system:task:run", "/api/admin/task/run.htm");

    Set<String> mappedApiPaths = new HashSet<>();
    resourceApiMappings.values().forEach(mappedApiPaths::addAll);
    for (String path : PROTECTED_API_PATHS) {
      assertTrue(mappedApiPaths.contains(path), "受保护接口未关联资源：" + path);
    }
  }

  /** 权限数据和快照版本必须在同一事务内变更，且基础账号数据不计入权限变化。 */
  @Test
  void 数据SQL原子推进权限快照版本() throws IOException {
    String sql = Files.readString(findSql());

    assertTrue(sql.contains("START TRANSACTION;"));
    assertTrue(sql.contains("COMMIT;"));
    assertTrue(sql.contains("SET @permission_changed = 0;"));
    assertFalse(
        sql.contains("SET @permission_changed = @permission_changed + ROW_COUNT();\n\n-- 默认管理员"));
    assertTrue(sql.contains("AND @permission_changed > 0;"));
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

  private Path findSql() {
    return findSql("2026080301-system-data-reinit.sql");
  }

  private Map<String, Set<String>> parseResourceApiMappings(String sql) {
    Matcher definitionMatcher = RESOURCE_API_DEFINITION_PATTERN.matcher(sql);
    assertTrue(definitionMatcher.find(), "数据 SQL 缺少 tmp_resource_api_definition 映射定义");

    Map<String, Set<String>> mappings = new HashMap<>();
    Matcher valueMatcher = RESOURCE_API_VALUE_PATTERN.matcher(definitionMatcher.group(1));
    while (valueMatcher.find()) {
      mappings
          .computeIfAbsent(valueMatcher.group(1), key -> new HashSet<>())
          .add(valueMatcher.group(2));
    }
    assertFalse(mappings.isEmpty(), "数据 SQL 的资源接口映射不能为空");
    return mappings;
  }

  private Set<String> parseSystemApiPaths(String sql) {
    Matcher definitionMatcher = SYSTEM_API_DEFINITION_PATTERN.matcher(sql);
    assertTrue(definitionMatcher.find(), "数据 SQL 缺少 tmp_system_api_definition 接口定义");

    Set<String> apiPaths = new HashSet<>();
    Matcher valueMatcher = SYSTEM_API_VALUE_PATTERN.matcher(definitionMatcher.group(1));
    while (valueMatcher.find()) {
      apiPaths.add(valueMatcher.group(1));
    }
    assertFalse(apiPaths.isEmpty(), "数据 SQL 的系统接口定义不能为空");
    return apiPaths;
  }

  private void assertMapping(
      Map<String, Set<String>> mappings, String resourceCode, String... apiPaths) {
    assertEquals(Set.of(apiPaths), mappings.get(resourceCode), "资源接口映射不正确：" + resourceCode);
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
