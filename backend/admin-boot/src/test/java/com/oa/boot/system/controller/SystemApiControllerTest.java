package com.oa.boot.system.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oa.action.controller.system.SystemApiController;
import com.oa.boot.advice.GlobalExceptionHandler;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.service.system.SystemApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

/** 系统接口目录参数绑定测试。 */
class SystemApiControllerTest {
  @Mock private SystemApiService systemApiService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    ProxyFactory proxyFactory = new ProxyFactory(new SystemApiController(systemApiService));
    proxyFactory.addAdvice(new MethodValidationInterceptor());
    mockMvc =
        MockMvcBuilders.standaloneSetup(proxyFactory.getProxy())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void 分页参数越界返回422() throws Exception {
    mockMvc
        .perform(get("/api/system/apis/page?page=0&size=201"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }

  @Test
  void 详情原始ID非法返回422() throws Exception {
    mockMvc
        .perform(get("/api/system/apis/detail?id=0"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }

  @Test
  void 状态请求字段非法返回422() throws Exception {
    mockMvc
        .perform(
            post("/api/system/apis/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":0,\"status\":null}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }
}
