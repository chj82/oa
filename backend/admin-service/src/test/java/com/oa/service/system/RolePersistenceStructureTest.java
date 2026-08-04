package com.oa.service.system;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 角色持久化结构门禁测试，不替代真实MySQL集成测试。 */
class RolePersistenceStructureTest {
  /** 角色DDL必须保留编码和名称唯一索引。 */
  @Test
  void 角色表包含编码和名称唯一索引() throws IOException {
    String ddl =
        Files.readString(
            existing(
                Path.of("../sql/schema.sql"),
                Path.of("sql/schema.sql"),
                Path.of("backend/sql/schema.sql")));
    assertTrue(ddl.contains("UNIQUE KEY udx_role_code (code)"));
    assertTrue(ddl.contains("UNIQUE KEY udx_role_name (name)"));
  }

  /** 角色分页使用单表Mapper并覆盖编码、名称和状态条件。 */
  @Test
  void 角色分页保持单表查询条件() throws IOException {
    Path mapper =
        existing(
            Path.of("../admin-dao/src/main/java/com/oa/dao/system/RoleMapper.java"),
            Path.of("admin-dao/src/main/java/com/oa/dao/system/RoleMapper.java"),
            Path.of("backend/admin-dao/src/main/java/com/oa/dao/system/RoleMapper.java"));
    String source = Files.readString(mapper);
    assertTrue(source.contains("selectRolePage"));
    assertTrue(source.contains("RoleEntity::getCode"));
    assertTrue(source.contains("RoleEntity::getName"));
    assertTrue(source.contains("RoleEntity::getStatus"));
    assertFalse(source.toUpperCase().contains(" JOIN "));
  }

  /** 无外键关联写只允许两个角色锁方法，且集合锁按ID排序。 */
  @Test
  void 角色关联写锁范围明确且顺序固定() throws IOException {
    Path mapperXml =
        existing(
            Path.of("../admin-dao/src/main/resources/mapper/system/RoleMapper.xml"),
            Path.of("backend/admin-dao/src/main/resources/mapper/system/RoleMapper.xml"));
    String xml = Files.readString(mapperXml);
    assertTrue(xml.contains("selectByIdForUpdate"));
    assertTrue(xml.contains("selectByIdsForUpdate"));
    assertTrue(xml.contains("ORDER BY id"));
    assertTrue(xml.chars().filter(character -> character == '<').count() > 0);
    assertTrue(count(xml, "FOR UPDATE") == 2);
    assertFalse(xml.toUpperCase().contains(" JOIN "));
  }

  /** 员工删除和角色保存只允许通过单表员工行锁保护。 */
  @Test
  void 员工关联写使用明确单表行锁() throws IOException {
    Path mapperXml =
        existing(
            Path.of("../admin-dao/src/main/resources/mapper/system/EmployeeMapper.xml"),
            Path.of("backend/admin-dao/src/main/resources/mapper/system/EmployeeMapper.xml"));
    String xml = Files.readString(mapperXml);
    assertTrue(xml.contains("<select id=\"selectByIdForUpdate\""));
    assertTrue(count(xml, "FOR UPDATE") == 1);
    assertFalse(xml.toUpperCase().contains(" JOIN "));
  }

  /** 两类关联写必须使用MyBatis多VALUES批量SQL。 */
  @Test
  void 关联写使用单表批量插入() throws IOException {
    for (String name : List.of("RoleResourceMapper.xml", "EmployeeRoleMapper.xml")) {
      Path mapperXml =
          existing(
              Path.of("../admin-dao/src/main/resources/mapper/system/" + name),
              Path.of("backend/admin-dao/src/main/resources/mapper/system/" + name));
      String xml = Files.readString(mapperXml);
      assertTrue(xml.contains("<insert id=\"insertBatch\">"));
      assertTrue(xml.contains("<foreach collection=\"relations\""));
      assertFalse(xml.toUpperCase().contains(" JOIN "));
    }
  }

  private int count(String source, String value) {
    return (source.length() - source.replace(value, "").length()) / value.length();
  }

  private Path existing(Path... candidates) {
    for (Path candidate : candidates) {
      if (Files.exists(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("未找到角色持久化文件");
  }
}
