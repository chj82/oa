package com.oa.boot.system.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oa.action.controller.system.RoleController;
import com.oa.boot.advice.GlobalExceptionHandler;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.service.system.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

/** 角色接口参数绑定测试。 */
class RoleControllerTest {
  @Mock private RoleService roleService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    ProxyFactory proxyFactory = new ProxyFactory(new RoleController(roleService));
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
        .perform(get("/api/system/roles/detail?id=0"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }

  /** 删除ID为负数时返回422。 */
  @Test
  void deleteWithNegativeIdShouldReturn422() throws Exception {
    mockMvc
        .perform(post("/api/system/roles/delete?id=-1"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }

  /** 授权回显角色ID非法时返回422。 */
  @Test
  void resourceIdsWithZeroRoleIdShouldReturn422() throws Exception {
    mockMvc
        .perform(get("/api/system/roles/resource-ids?roleId=0"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }

  /** 分页参数越界时返回422。 */
  @Test
  void invalidPageShouldReturn422() throws Exception {
    mockMvc
        .perform(get("/api/system/roles/page?page=0&size=201"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }

  /** 新增请求字段非法时返回422。 */
  @Test
  void invalidCreateBodyShouldReturn422() throws Exception {
    expectInvalidBody(
        "/api/system/roles/create", "{\"code\":\" \",\"name\":\" \",\"status\":null}");
  }

  /** 修改请求ID非法时返回422。 */
  @Test
  void invalidUpdateBodyShouldReturn422() throws Exception {
    expectInvalidBody(
        "/api/system/roles/update",
        "{\"id\":0,\"code\":\"admin\",\"name\":\"管理员\",\"status\":\"ENABLED\"}");
  }

  /** 状态请求ID非法时返回422。 */
  @Test
  void invalidStatusBodyShouldReturn422() throws Exception {
    expectInvalidBody("/api/system/roles/status", "{\"id\":-1,\"status\":\"DISABLED\"}");
  }

  /** 资源授权包含非法ID时返回422。 */
  @Test
  void invalidResourcesBodyShouldReturn422() throws Exception {
    mockMvc
        .perform(
            post("/api/system/roles/resources?id=1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[1,null,0]}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }

  private void expectInvalidBody(String path, String body) throws Exception {
    mockMvc
        .perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }
}
