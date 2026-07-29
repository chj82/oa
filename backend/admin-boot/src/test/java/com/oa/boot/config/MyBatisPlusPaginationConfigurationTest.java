package com.oa.boot.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;

/** MyBatis-Plus分页装配测试。 */
class MyBatisPlusPaginationConfigurationTest {
  /** Boot模块必须提供MySQL分页拦截器。 */
  @Test
  void shouldConfigureMysqlPaginationInterceptor() throws Exception {
    Class<?> configurationClass = Class.forName("com.oa.boot.config.MyBatisPlusConfiguration");
    assertThat(configurationClass.getAnnotation(Configuration.class)).isNotNull();
    Object configuration = configurationClass.getConstructor().newInstance();
    Method factoryMethod = configurationClass.getMethod("mybatisPlusInterceptor");

    MybatisPlusInterceptor interceptor =
        (MybatisPlusInterceptor) factoryMethod.invoke(configuration);

    assertThat(interceptor.getInterceptors())
        .singleElement()
        .isInstanceOf(PaginationInnerInterceptor.class);
  }
}
