package com.oa.boot;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 应用启动入口测试。 */
class OaApplicationTest {

  /** 启动类必须存在，供 Spring Boot 插件定位入口。 */
  @Test
  void applicationClassExists() {
    assertNotNull(OaApplication.class);
  }

  /** 启动类必须启用权限快照版本定时校准。 */
  @Test
  void applicationEnablesScheduling() {
    assertTrue(OaApplication.class.isAnnotationPresent(EnableScheduling.class));
  }
}
