package com.oa.service.system;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 系统接口目录持久化边界测试。 */
class SystemApiPersistenceStructureTest {
  @Test
  void 分页和写操作保持单表且不使用行锁() throws IOException {
    String source =
        Files.readString(
            existing(
                Path.of("../admin-dao/src/main/java/com/oa/dao/system/SystemApiMapper.java"),
                Path.of("admin-dao/src/main/java/com/oa/dao/system/SystemApiMapper.java"),
                Path.of("backend/admin-dao/src/main/java/com/oa/dao/system/SystemApiMapper.java")));

    assertTrue(source.contains("selectSystemApiPage"));
    assertTrue(source.contains("SystemApiEntity::getName"));
    assertTrue(source.contains("SystemApiEntity::getPath"));
    assertTrue(source.contains("SystemApiEntity::getStatus"));
    assertTrue(source.contains("updateMetadata"));
    assertTrue(source.contains("updateStatusIfCurrent"));
    assertFalse(source.toUpperCase().contains(" JOIN "));
    assertFalse(source.toUpperCase().contains("FOR UPDATE"));
  }

  @Test
  void Service不构造Wrapper或内嵌SQL() throws IOException {
    String source =
        Files.readString(
            existing(
                Path.of("src/main/java/com/oa/service/system/SystemApiService.java"),
                Path.of("admin-service/src/main/java/com/oa/service/system/SystemApiService.java"),
                Path.of(
                    "backend/admin-service/src/main/java/com/oa/service/system/SystemApiService.java")));

    assertFalse(source.contains("Wrappers"));
    assertFalse(source.contains("QueryWrapper"));
    assertFalse(source.contains("LambdaQueryWrapper"));
    assertFalse(source.toUpperCase().contains(" JOIN "));
    assertFalse(source.toUpperCase().contains("FOR UPDATE"));
  }

  private Path existing(Path... candidates) {
    for (Path candidate : candidates) {
      if (Files.exists(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("未找到系统接口目录持久化文件");
  }
}
