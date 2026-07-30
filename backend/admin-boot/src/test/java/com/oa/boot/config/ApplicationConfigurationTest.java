package com.oa.boot.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ApplicationConfigurationTest {
  @Test
  void 不包含代码初始化配置() throws IOException {
    String applicationYaml;
    try (InputStream input =
        Thread.currentThread().getContextClassLoader().getResourceAsStream("application.yml")) {
      applicationYaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    assertFalse(applicationYaml.contains("command:"));
    assertFalse(applicationYaml.contains("initial-admin-username:"));
    assertFalse(applicationYaml.contains("initial-admin-password:"));
    assertTrue(applicationYaml.contains("default-timeout: 30s"));
  }
}
