package com.oa.boot.system.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oa.action.controller.system.DepartmentController;
import com.oa.boot.advice.GlobalExceptionHandler;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.service.system.DepartmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

/** 部门接口参数绑定测试。 */
class DepartmentControllerTest {
  @Mock private DepartmentService departmentService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    ProxyFactory proxyFactory = new ProxyFactory(new DepartmentController(departmentService));
    proxyFactory.addAdvice(new MethodValidationInterceptor());
    mockMvc =
        MockMvcBuilders.standaloneSetup(proxyFactory.getProxy())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  /** 详情ID为零时返回422。 */
  @Test
  void detailWithZeroIdShouldReturn422() throws Exception {
    mockMvc
        .perform(get("/api/system/departments/detail?id=0"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }

  /** 删除ID为负数时返回422。 */
  @Test
  void deleteWithNegativeIdShouldReturn422() throws Exception {
    mockMvc
        .perform(post("/api/system/departments/delete?id=-1"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }
}
