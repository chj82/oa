package com.oa.boot.config;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ApplicationConfigurationTest {
  @Test
  void 数据库和Redis密码不提供固定默认值() throws IOException {
    String applicationYaml;
    try (InputStream input =
        Thread.currentThread().getContextClassLoader().getResourceAsStream("application.yml")) {
      applicationYaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    assertFalse(applicationYaml.contains("${MYSQL_PASSWORD:12345678}"));
    assertFalse(applicationYaml.contains("${REDIS_PASSWORD:12345678}"));
  }
}
