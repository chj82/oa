package com.oa.boot.system.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oa.action.controller.system.ResourceController;
import com.oa.boot.advice.GlobalExceptionHandler;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.service.system.ResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

/** 系统资源接口参数绑定测试。 */
class ResourceControllerTest {
  @Mock private ResourceService resourceService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    ProxyFactory proxyFactory = new ProxyFactory(new ResourceController(resourceService));
    proxyFactory.addAdvice(new MethodValidationInterceptor());
    mockMvc =
        MockMvcBuilders.standaloneSetup(proxyFactory.getProxy())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  /** 详情原始ID为零时返回422。 */
  @Test
  void detailWithZeroIdShouldReturn422() throws Exception {
    mockMvc
        .perform(get("/api/system/resources/detail?id=0"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }

  /** 保存资源接口请求字段不合法时返回422。 */
  @Test
  void saveApisWithInvalidBodyShouldReturn422() throws Exception {
    mockMvc
        .perform(
            post("/api/system/resources/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceId\":0,\"apiIds\":[0]}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }
}
