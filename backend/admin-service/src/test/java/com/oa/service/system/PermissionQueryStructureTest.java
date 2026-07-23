package com.oa.service.system;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** 权限查询结构门禁测试。 */
class PermissionQueryStructureTest {
  private static final Pattern SELECT_PATTERN =
      Pattern.compile("(?is)<select\\b[^>]*>(.*?)</select>");
  private static final Pattern TABLE_PATTERN =
      Pattern.compile("(?i)\\b(?:FROM|JOIN)\\s+t_[a-z0-9_]+");

  /** 权限链必须使用语义化单表 Mapper 方法，不保留多表权限 Mapper。 */
  @Test
  void 权限链不使用关联查询() throws IOException {
    assertFalse(
        Files.exists(daoResourceRoot().resolve("mapper/system/PermissionMapper.xml")),
        "权限链不得保留 PermissionMapper.xml");
    assertFalse(
        Files.exists(daoSourceRoot().resolve("com/oa/dao/system/PermissionMapper.java")),
        "权限链不得保留无用 PermissionMapper");

    String mapperSources = readFiles(daoSourceRoot(), ".java");
    for (String method :
        new String[] {
          "selectRoleIdsByEmployeeId",
          "selectEnabledIds",
          "selectResourceIdsByRoleIds",
          "selectExistingByIds",
          "selectApiIdsByResourceIds",
          "selectEnabledPathsByIds",
          "selectEnabledPaths"
        }) {
      assertTrue(mapperSources.contains(method), "缺少语义化单表 Mapper 方法：" + method);
    }
  }

  /** 所有 Mapper XML 中的单条查询最多涉及三张表。 */
  @Test
  void MapperXml单条查询最多三张表() throws IOException {
    Path resourceRoot = daoResourceRoot();
    if (!Files.isDirectory(resourceRoot)) {
      return;
    }
    try (var files = Files.walk(resourceRoot)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".xml")).toList()) {
        Matcher selects = SELECT_PATTERN.matcher(Files.readString(file));
        while (selects.find()) {
          Matcher tables = TABLE_PATTERN.matcher(selects.group(1));
          int tableCount = 0;
          while (tables.find()) {
            tableCount++;
          }
          assertTrue(tableCount <= 3, file + " 单条查询涉及 " + tableCount + " 张表");
        }
      }
    }
  }

  private String readFiles(Path root, String suffix) throws IOException {
    StringBuilder content = new StringBuilder();
    try (var files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(suffix)).toList()) {
        content.append(Files.readString(file));
      }
    }
    return content.toString();
  }

  private Path daoSourceRoot() {
    return existing(
        Path.of("../admin-dao/src/main/java"),
        Path.of("admin-dao/src/main/java"),
        Path.of("backend/admin-dao/src/main/java"));
  }

  private Path daoResourceRoot() {
    return existingOrFirst(
        Path.of("../admin-dao/src/main/resources"),
        Path.of("admin-dao/src/main/resources"),
        Path.of("backend/admin-dao/src/main/resources"));
  }

  private Path existing(Path... candidates) {
    Path path = existingOrFirst(candidates);
    if (Files.isDirectory(path)) {
      return path;
    }
    throw new IllegalStateException("未找到 DAO 源码目录");
  }

  private Path existingOrFirst(Path... candidates) {
    for (Path candidate : candidates) {
      if (Files.exists(candidate)) {
        return candidate;
      }
    }
    return candidates[0];
  }
}
