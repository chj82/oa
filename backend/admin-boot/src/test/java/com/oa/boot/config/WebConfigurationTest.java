package com.oa.boot.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oa.boot.security.ResourceApiAuthorizationInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class WebConfigurationTest {
  private AnnotationConfigWebApplicationContext context;
  private MockMvc mockMvc;
  private ResourceApiAuthorizationInterceptor interceptor;

  @BeforeEach
  void setUp() throws Exception {
    interceptor = org.mockito.Mockito.mock(ResourceApiAuthorizationInterceptor.class);
    when(interceptor.preHandle(any(), any(), any())).thenReturn(true);
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(TestMvcConfiguration.class, CorsTestController.class);
    context.addBeanFactoryPostProcessor(
        beanFactory ->
            beanFactory.registerSingleton(
                "webConfiguration", new WebConfiguration("http://localhost:3000/", interceptor)));
    context.refresh();
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @AfterEach
  void tearDown() {
    context.close();
  }

  @Test
  void 配置来源请求回显单一Origin并允许凭据() throws Exception {
    mockMvc
        .perform(get("/cors-test").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
  }

  @Test
  void Options预检允许配置来源() throws Exception {
    mockMvc
        .perform(
            options("/cors-test")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
  }

  @Test
  void 非配置来源不回显Origin() throws Exception {
    mockMvc
        .perform(get("/cors-test").header(HttpHeaders.ORIGIN, "http://evil.example"))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  void 业务接口注册资源鉴权拦截器() throws Exception {
    mockMvc.perform(get("/cors-test")).andExpect(status().isOk());

    verify(interceptor).preHandle(any(), any(), any());
  }

  @Test
  void 固定公开和仅登录路径不进入资源鉴权() throws Exception {
    for (String path :
        new String[] {
          "/api/auth/login",
          "/api/auth/current",
          "/api/auth/logout",
          "/error",
          "/swagger-ui.html",
          "/swagger-ui",
          "/swagger-ui/index.html",
          "/v3/api-docs",
          "/v3/api-docs/swagger-config"
        }) {
      mockMvc.perform(get(path)).andExpect(status().isOk());
    }

    verify(interceptor, never()).preHandle(any(), any(), any());
  }

  @Test
  void Swagger相似前缀仍进入资源鉴权() throws Exception {
    mockMvc.perform(get("/swagger-uiAnything")).andExpect(status().isOk());
    mockMvc.perform(get("/v3/api-docsAnything")).andExpect(status().isOk());

    verify(interceptor, org.mockito.Mockito.times(2)).preHandle(any(), any(), any());
  }

  @RestController
  static class CorsTestController {
    @GetMapping({
      "/cors-test",
      "/api/auth/login",
      "/api/auth/current",
      "/api/auth/logout",
      "/error",
      "/swagger-ui.html",
      "/swagger-ui",
      "/swagger-ui/index.html",
      "/swagger-uiAnything",
      "/v3/api-docs",
      "/v3/api-docs/swagger-config",
      "/v3/api-docsAnything"
    })
    String get() {
      return "ok";
    }
  }

  @Configuration
  @EnableWebMvc
  static class TestMvcConfiguration {}
}
