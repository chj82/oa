package com.oa.service.system.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Redis 登录会话序列化配置。 */
@Configuration
public class RedisSessionConfiguration {
  @Bean
  ObjectMapper loginSessionObjectMapper() {
    return new ObjectMapper();
  }
}
