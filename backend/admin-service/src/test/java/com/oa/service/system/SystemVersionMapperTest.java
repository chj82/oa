package com.oa.service.system;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 系统版本数据访问结构测试。 */
class SystemVersionMapperTest {

  /** 版本更新必须按版本编码使用数据库原子递增。 */
  @Test
  void 版本使用单表原子递增() throws Exception {
    String xml = Files.readString(findFile("SystemVersionMapper.xml"));

    assertTrue(xml.contains("SET version_value = version_value + 1"));
    assertTrue(xml.contains("WHERE version_code = #{versionCode}"));
    assertTrue(xml.contains("SELECT version_value"));
  }

  /** Mapper默认方法必须拒绝版本记录缺失。 */
  @Test
  void 版本记录缺失保护保留在Mapper边界() throws Exception {
    String source = Files.readString(findFile("SystemVersionMapper.java"));

    assertTrue(source.contains("incrementVersion(versionCode, LocalDateTime.now()) != 1"));
    assertTrue(source.contains("系统版本不存在，编码="));
    assertTrue(source.contains("return selectVersion(versionCode)"));
  }

  private Path findFile(String filename) {
    for (Path root :
        List.of(
            Path.of("backend/admin-dao/src/main"),
            Path.of("../admin-dao/src/main"),
            Path.of("admin-dao/src/main"))) {
      try (var files = Files.walk(root)) {
        Path result =
            files
                .filter(path -> path.getFileName().toString().equals(filename))
                .findFirst()
                .orElse(null);
        if (result != null) {
          return result;
        }
      } catch (java.io.IOException ignored) {
        // 尝试下一个 Maven 执行目录。
      }
    }
    throw new IllegalStateException("未找到文件：" + filename);
  }
}
