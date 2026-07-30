package com.oa.boot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.oa.boot.security.TokenAuthenticationFilter;
import com.oa.service.system.PermissionService;
import com.oa.service.system.SessionService;
import com.oa.service.system.store.RedisCacheJsonCodec;
import com.oa.service.system.store.StringRedisSessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class AuthenticationAssemblyTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(JacksonAutoConfiguration.class, RedisAutoConfiguration.class))
          .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
          .withBean(PermissionService.class, () -> mock(PermissionService.class))
          .withUserConfiguration(AuthenticationTestConfiguration.class)
          .withPropertyValues("app.security.frontend-origin=http://localhost:3000");

  @Test
  void Redis会话服务和认证过滤器无歧义装配() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(RedisCacheJsonCodec.class);
          assertThat(context).hasSingleBean(StringRedisTemplate.class);
          assertThat(context).hasSingleBean(StringRedisSessionStore.class);
          assertThat(context).hasSingleBean(SessionService.class);
          assertThat(context).hasSingleBean(TokenAuthenticationFilter.class);
        });
  }

  @Configuration(proxyBeanMethods = false)
  @Import({
    RedisCacheJsonCodec.class,
    StringRedisSessionStore.class,
    SessionService.class,
    TokenAuthenticationFilter.class
  })
  static class AuthenticationTestConfiguration {}
}
