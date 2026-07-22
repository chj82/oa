package com.oa.boot;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** 应用启动入口测试。 */
class OaApplicationTest {

  /** 启动类必须存在，供 Spring Boot 插件定位入口。 */
  @Test
  void applicationClassExists() {
    assertNotNull(OaApplication.class);
  }
}
