package com.oa.entity.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 系统实体映射测试。 */
class EntityMappingTest {

  /** 八个实体的表名和全部字段必须与设计一致。 */
  @Test
  void shouldMapAllDesignedTablesAndColumns() throws NoSuchFieldException {
    Map<Class<?>, TableMapping> mappings =
        Map.of(
            EmployeeEntity.class,
            new TableMapping(
                "t_employee",
                columns(
                    "id",
                    "id",
                    "username",
                    "username",
                    "name",
                    "name",
                    "passwordHash",
                    "password_hash",
                    "phone",
                    "phone",
                    "email",
                    "email",
                    "departmentId",
                    "department_id",
                    "status",
                    "status",
                    "superuser",
                    "is_superuser",
                    "createdAt",
                    "created_at",
                    "updatedAt",
                    "updated_at")),
            DepartmentEntity.class,
            new TableMapping(
                "t_department",
                columns(
                    "id",
                    "id",
                    "parentId",
                    "parent_id",
                    "name",
                    "name",
                    "sortOrder",
                    "sort_order",
                    "status",
                    "status",
                    "createdAt",
                    "created_at",
                    "updatedAt",
                    "updated_at")),
            RoleEntity.class,
            new TableMapping(
                "t_role",
                columns(
                    "id",
                    "id",
                    "code",
                    "code",
                    "name",
                    "name",
                    "description",
                    "description",
                    "status",
                    "status",
                    "createdAt",
                    "created_at",
                    "updatedAt",
                    "updated_at")),
            SystemResourceEntity.class,
            new TableMapping(
                "t_system_resource",
                columns(
                    "id",
                    "id",
                    "parentId",
                    "parent_id",
                    "type",
                    "type",
                    "name",
                    "name",
                    "code",
                    "code",
                    "path",
                    "path",
                    "icon",
                    "icon",
                    "sortOrder",
                    "sort_order",
                    "visible",
                    "visible",
                    "status",
                    "status",
                    "createdAt",
                    "created_at",
                    "updatedAt",
                    "updated_at")),
            SystemApiEntity.class,
            new TableMapping(
                "t_system_api",
                columns(
                    "id",
                    "id",
                    "name",
                    "name",
                    "path",
                    "path",
                    "description",
                    "description",
                    "status",
                    "status",
                    "createdAt",
                    "created_at",
                    "updatedAt",
                    "updated_at")),
            EmployeeRoleEntity.class,
            new TableMapping(
                "t_employee_role",
                columns(
                    "id",
                    "id",
                    "employeeId",
                    "employee_id",
                    "roleId",
                    "role_id",
                    "createdAt",
                    "created_at")),
            RoleResourceEntity.class,
            new TableMapping(
                "t_role_resource",
                columns(
                    "id",
                    "id",
                    "roleId",
                    "role_id",
                    "resourceId",
                    "resource_id",
                    "createdAt",
                    "created_at")),
            ResourceApiEntity.class,
            new TableMapping(
                "t_resource_api",
                columns(
                    "id",
                    "id",
                    "resourceId",
                    "resource_id",
                    "apiId",
                    "api_id",
                    "createdAt",
                    "created_at")));
    for (var mapping : mappings.entrySet()) {
      assertEquals(
          mapping.getValue().table(), mapping.getKey().getAnnotation(TableName.class).value());
      Set<String> actualFields =
          java.util.Arrays.stream(mapping.getKey().getDeclaredFields())
              .filter(field -> !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic())
              .map(Field::getName)
              .collect(Collectors.toSet());
      assertEquals(mapping.getValue().columns().keySet(), actualFields, mapping.getKey().getName());
      for (var column : mapping.getValue().columns().entrySet()) {
        assertField(mapping.getKey(), column.getKey(), column.getValue());
      }
    }
  }

  /** 数据库非空数值字段必须使用基本类型，明确允许为空的字段保留包装类型。 */
  @Test
  void shouldUseNullabilityAwareNumericTypes() {
    assertFieldTypes(
        DepartmentEntity.class,
        Map.of(
            "id", long.class, "parentId", long.class, "sortOrder", int.class, "status", int.class));
    assertFieldTypes(
        EmployeeEntity.class,
        Map.of(
            "id", long.class,
            "departmentId", long.class,
            "status", int.class,
            "superuser", int.class));
    assertFieldTypes(RoleEntity.class, Map.of("id", long.class, "status", int.class));
    assertFieldTypes(
        SystemResourceEntity.class,
        Map.of(
            "id", long.class,
            "parentId", long.class,
            "sortOrder", int.class,
            "visible", int.class,
            "status", int.class));
    assertFieldTypes(SystemApiEntity.class, Map.of("id", long.class, "status", int.class));
    assertFieldTypes(
        EmployeeRoleEntity.class,
        Map.of("id", long.class, "employeeId", long.class, "roleId", long.class));
    assertFieldTypes(
        RoleResourceEntity.class,
        Map.of("id", long.class, "roleId", long.class, "resourceId", long.class));
    assertFieldTypes(
        ResourceApiEntity.class,
        Map.of("id", long.class, "resourceId", long.class, "apiId", long.class));
    assertFieldType(SystemResourceEntity.class, "type", String.class);
  }

  /** 时间字段只映射数据库列，不声明未配置处理器的自动填充。 */
  @Test
  void shouldNotDeclareAutomaticTimeFill() throws NoSuchFieldException {
    List.of(
            DepartmentEntity.class,
            EmployeeEntity.class,
            RoleEntity.class,
            SystemResourceEntity.class,
            SystemApiEntity.class,
            EmployeeRoleEntity.class,
            RoleResourceEntity.class,
            ResourceApiEntity.class)
        .forEach(type -> assertTimeFieldWithoutFill(type, "createdAt"));
    List.of(
            DepartmentEntity.class,
            EmployeeEntity.class,
            RoleEntity.class,
            SystemResourceEntity.class,
            SystemApiEntity.class)
        .forEach(type -> assertTimeFieldWithoutFill(type, "updatedAt"));
  }

  /** 关联实体必须使用统一自增主键，Mapper 使用标准单主键能力。 */
  @Test
  void shouldMapAssociationAutoIncrementIds() throws Exception {
    for (Class<?> type :
        List.of(EmployeeRoleEntity.class, RoleResourceEntity.class, ResourceApiEntity.class)) {
      Field id = type.getDeclaredField("id");
      TableId tableId = id.getAnnotation(TableId.class);
      assertEquals("id", tableId.value(), type.getSimpleName());
      assertEquals(com.baomidou.mybatisplus.annotation.IdType.AUTO, tableId.type());
    }
  }

  /** 任务二生产和测试源码不得声明 record。 */
  @Test
  void shouldNotDeclareRecords() throws Exception {
    for (Path root : List.of(Path.of("backend"), Path.of(".."))) {
      if (Files.isDirectory(root.resolve("admin-common"))) {
        try (var files = Files.walk(root)) {
          for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
            org.junit.jupiter.api.Assertions.assertFalse(
                Files.readString(file).contains("rec" + "ord "), file.toString());
          }
        }
        return;
      }
    }
    throw new IllegalStateException("未找到后端源码目录");
  }

  /** DDL 必须包含全部实体对应表，且接口表不得出现额外业务字段。 */
  @Test
  void shouldKeepSqlTableNamesConsistent() throws Exception {
    Path sqlFile = Path.of("backend/sql/2026072201-system-init.sql");
    if (!Files.exists(sqlFile)) {
      sqlFile = Path.of("../sql/2026072201-system-init.sql");
    }
    String ddl = Files.readString(sqlFile);
    List.of(
            "t_employee",
            "t_department",
            "t_role",
            "t_system_resource",
            "t_system_api",
            "t_employee_role",
            "t_role_resource",
            "t_resource_api")
        .forEach(
            table ->
                org.junit.jupiter.api.Assertions.assertTrue(
                    ddl.contains("CREATE TABLE " + table + " ("), table));
    String apiTable =
        ddl.substring(
            ddl.indexOf("CREATE TABLE t_system_api"), ddl.indexOf("CREATE TABLE t_employee_role"));
    org.junit.jupiter.api.Assertions.assertFalse(apiTable.contains("method"));
    org.junit.jupiter.api.Assertions.assertFalse(apiTable.contains("code"));
    org.junit.jupiter.api.Assertions.assertTrue(
        ddl.contains("UNIQUE KEY udx_employee_role_employee_role (employee_id, role_id)"));
    org.junit.jupiter.api.Assertions.assertTrue(
        ddl.contains("UNIQUE KEY udx_role_resource_role_resource (role_id, resource_id)"));
    org.junit.jupiter.api.Assertions.assertTrue(
        ddl.contains("UNIQUE KEY udx_resource_api_resource_api (resource_id, api_id)"));
    for (String table : List.of("t_employee_role", "t_role_resource", "t_resource_api")) {
      String tableDdl = tableDdl(ddl, table);
      org.junit.jupiter.api.Assertions.assertTrue(
          tableDdl.contains("id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT"), table);
      org.junit.jupiter.api.Assertions.assertTrue(tableDdl.contains("PRIMARY KEY (id)"), table);
    }
  }

  private void assertField(Class<?> type, String fieldName, String columnName)
      throws NoSuchFieldException {
    Field field = type.getDeclaredField(fieldName);
    TableId tableId = field.getAnnotation(TableId.class);
    String actual =
        tableId == null ? field.getAnnotation(TableField.class).value() : tableId.value();
    assertEquals(columnName, actual, type.getSimpleName() + "." + fieldName);
  }

  private Map<String, String> columns(String... names) {
    java.util.LinkedHashMap<String, String> columns = new java.util.LinkedHashMap<>();
    for (int index = 0; index < names.length; index += 2) {
      columns.put(names[index], names[index + 1]);
    }
    return columns;
  }

  private void assertFieldType(Class<?> type, String fieldName, Class<?> expectedType) {
    try {
      assertEquals(expectedType, type.getDeclaredField(fieldName).getType(), type.getSimpleName());
    } catch (NoSuchFieldException exception) {
      throw new AssertionError(exception);
    }
  }

  private void assertFieldTypes(Class<?> type, Map<String, Class<?>> expectedTypes) {
    expectedTypes.forEach((fieldName, fieldType) -> assertFieldType(type, fieldName, fieldType));
  }

  private void assertTimeFieldWithoutFill(Class<?> type, String fieldName) {
    try {
      assertEquals(
          FieldFill.DEFAULT,
          type.getDeclaredField(fieldName).getAnnotation(TableField.class).fill());
    } catch (NoSuchFieldException exception) {
      throw new AssertionError(exception);
    }
  }

  private String tableDdl(String ddl, String table) {
    int start = ddl.indexOf("CREATE TABLE " + table);
    return ddl.substring(start, ddl.indexOf("ENGINE=InnoDB", start));
  }

  private static class TableMapping {
    private final String table;
    private final Map<String, String> columns;

    private TableMapping(String table, Map<String, String> columns) {
      this.table = table;
      this.columns = columns;
    }

    private String table() {
      return table;
    }

    private Map<String, String> columns() {
      return columns;
    }
  }
}
