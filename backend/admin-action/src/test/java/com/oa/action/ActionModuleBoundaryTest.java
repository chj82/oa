package com.oa.action;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ActionModuleBoundaryTest {
  @Test
  void 主源码只包含Controller实现() throws IOException {
    Path userDirectory = Path.of(System.getProperty("user.dir"));
    Path sourceRoot = userDirectory.resolve("src/main/java");
    if (!Files.isDirectory(sourceRoot)) {
      sourceRoot = userDirectory.resolve("backend/admin-action/src/main/java");
    }

    try (var files = Files.walk(sourceRoot)) {
      assertTrue(
          files
              .filter(path -> path.toString().endsWith(".java"))
              .allMatch(path -> path.toString().contains("/controller/")));
    }
  }
}
