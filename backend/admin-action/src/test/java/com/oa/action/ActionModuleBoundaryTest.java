package com.oa.action;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Action 模块源码边界测试。 */
class ActionModuleBoundaryTest {
  /** 主源码只能位于 com.oa.action.controller 下的业务模块包。 */
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
              .allMatch(this::isBusinessControllerSource));
    }
  }

  /** 旧的业务包在 controller 外层的目录必须被拒绝。 */
  @Test
  void 拒绝旧Controller目录顺序() {
    assertTrue(
        isBusinessControllerSource(
            Path.of("src/main/java/com/oa/action/controller/system/AuthController.java")));
    assertFalse(
        isBusinessControllerSource(
            Path.of("src/main/java/com/oa/action/system/controller/AuthController.java")));
  }

  private boolean isBusinessControllerSource(Path path) {
    return path.toString()
        .matches(
            ".*[/\\\\]com[/\\\\]oa[/\\\\]action[/\\\\]controller[/\\\\][^/\\\\]+[/\\\\][^/\\\\]+\\.java$");
  }
}
