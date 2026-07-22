package com.oa.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthenticationStructureTest {
  @Test
  void Service主源码不使用查询Wrapper注解SQL或内嵌SQL() throws IOException {
    String source = readJavaSources(serviceSourceRoot());
    for (String forbidden :
        List.of(
            "Wrappers",
            "QueryWrapper",
            "LambdaQueryWrapper",
            "com.baomidou.mybatisplus.core.conditions",
            "@Select")) {
      assertFalse(source.contains(forbidden), "禁止内容: " + forbidden);
    }
    assertFalse(
        source.matches("(?s).*\"[^\"]*\\b(SELECT|INSERT|UPDATE|DELETE|FROM|WHERE)\\b[^\"]*\".*"));
  }

  @Test
  void LoginSessionCache保持显式JavaBean规范() throws IOException {
    String source =
        Files.readString(
            commonSourceRoot().resolve("com/oa/common/model/system/cache/LoginSessionCache.java"));
    assertTrue(source.contains("public class LoginSessionCache"));
    assertFalse(source.contains("rec" + "ord "));
    assertFalse(source.contains("lombok"));
    assertFalse(source.contains("Map<"));
    assertTrue(source.contains("public LoginSessionCache()"));
    for (String field : List.of("employeeId", "username", "name", "superuser")) {
      assertTrue(
          source.matches(
              "(?s).*/\\*\\*[^*]*[\\u4e00-\\u9fa5][^*]*\\*/\\s+private [^;]+ " + field + ";.*"));
      assertTrue(
          source.contains(
              "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1) + "()"));
      assertTrue(
          source.contains(
              "set" + Character.toUpperCase(field.charAt(0)) + field.substring(1) + "("));
    }
  }

  private String readJavaSources(Path root) throws IOException {
    StringBuilder source = new StringBuilder();
    try (var files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        source.append(Files.readString(file));
      }
    }
    return source.toString();
  }

  private Path serviceSourceRoot() {
    return existing(
        Path.of("src/main/java"),
        Path.of("admin-service/src/main/java"),
        Path.of("backend/admin-service/src/main/java"));
  }

  private Path commonSourceRoot() {
    return existing(
        Path.of("../admin-common/src/main/java"),
        Path.of("admin-common/src/main/java"),
        Path.of("backend/admin-common/src/main/java"));
  }

  private Path existing(Path... candidates) {
    for (Path candidate : candidates) {
      if (Files.isDirectory(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("未找到源码目录");
  }
}
