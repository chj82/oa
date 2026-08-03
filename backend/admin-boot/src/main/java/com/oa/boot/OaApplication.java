package com.oa.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** OA 后台应用启动入口。 */
@MapperScan("com.oa.dao")
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.oa")
public class OaApplication {

  /**
   * 启动应用。
   *
   * @param args 启动参数
   */
  public static void main(String[] args) {
    SpringApplication.run(OaApplication.class, args);
  }
}
