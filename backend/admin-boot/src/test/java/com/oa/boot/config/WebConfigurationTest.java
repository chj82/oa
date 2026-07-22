package com.oa.boot.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
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

  @BeforeEach
  void setUp() {
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(
        TestMvcConfiguration.class, TestCorsConfiguration.class, CorsTestController.class);
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

  @RestController
  static class CorsTestController {
    @GetMapping("/cors-test")
    String get() {
      return "ok";
    }
  }

  @Configuration
  @EnableWebMvc
  static class TestMvcConfiguration {}

  @Configuration
  static class TestCorsConfiguration {
    @Bean
    WebConfiguration webConfiguration() {
      return new WebConfiguration("http://localhost:3000/");
    }
  }
}
