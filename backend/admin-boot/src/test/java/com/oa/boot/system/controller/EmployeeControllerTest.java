package com.oa.boot.system.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oa.action.controller.system.EmployeeController;
import com.oa.boot.advice.GlobalExceptionHandler;
import com.oa.common.context.CurrentEmployeeContext;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.EmployeeUpdateDTO;
import com.oa.common.model.system.vo.CurrentEmployeeVO;
import com.oa.service.system.EmployeeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

/** 员工接口参数绑定测试。 */
class EmployeeControllerTest {
  @Mock private EmployeeService employeeService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    CurrentEmployeeContext.set(new CurrentEmployeeVO());
    ProxyFactory proxyFactory = new ProxyFactory(new EmployeeController(employeeService));
    proxyFactory.addAdvice(new MethodValidationInterceptor());
    mockMvc =
        MockMvcBuilders.standaloneSetup(proxyFactory.getProxy())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @AfterEach
  void tearDown() {
    CurrentEmployeeContext.clear();
  }

  /** 修改请求缺少员工ID时返回422。 */
  @Test
  void updateWithoutIdShouldReturn422() throws Exception {
    mockMvc
        .perform(
            post("/api/system/employees/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validUpdateJsonWithoutId()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }

  /** 修改JSON中的未知password字段不会进入员工修改模型。 */
  @Test
  void updatePasswordPropertyShouldBeIgnoredByUpdateModel() throws Exception {
    mockMvc
        .perform(
            post("/api/system/employees/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"id":9,"username":"zhangsan","name":"张三","departmentId":3,
                     "status":"ENABLED","superuser":false,"password":"new-secret"}
                    """))
        .andExpect(status().isOk());

    ArgumentCaptor<EmployeeUpdateDTO> captor = ArgumentCaptor.forClass(EmployeeUpdateDTO.class);
    verify(employeeService).update(captor.capture(), any(CurrentEmployeeVO.class));
    assertThat(captor.getValue().getClass().getDeclaredFields())
        .extracting(java.lang.reflect.Field::getName)
        .doesNotContain("password");
  }

  /** 角色ID列表超过上限时返回422。 */
  @Test
  void tooManyRoleIdsShouldReturn422() throws Exception {
    String ids =
        java.util.stream.LongStream.rangeClosed(1, 201)
            .mapToObj(String::valueOf)
            .collect(java.util.stream.Collectors.joining(","));

    mockMvc
        .perform(
            post("/api/system/employees/roles?id=9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[" + ids + "]}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }

  /** 角色ID列表包含空元素时返回422。 */
  @Test
  void nullRoleIdShouldReturn422() throws Exception {
    mockMvc
        .perform(
            post("/api/system/employees/roles?id=9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[1,null]}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }

  /** 删除接口应把当前员工ID作为操作者传给服务。 */
  @Test
  void deleteShouldPassCurrentEmployeeId() throws Exception {
    CurrentEmployeeVO current = new CurrentEmployeeVO();
    current.setId(7L);
    CurrentEmployeeContext.set(current);

    mockMvc.perform(post("/api/system/employees/delete?id=9")).andExpect(status().isOk());

    verify(employeeService).delete(9L, 7L);
  }

  /** 详情ID为零时返回422。 */
  @Test
  void detailWithZeroIdShouldReturn422() throws Exception {
    mockMvc
        .perform(get("/api/system/employees/detail?id=0"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }

  /** 删除ID为负数时返回422。 */
  @Test
  void deleteWithNegativeIdShouldReturn422() throws Exception {
    mockMvc
        .perform(post("/api/system/employees/delete?id=-1"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }

  /** 角色回显ID为零时返回422。 */
  @Test
  void roleIdsWithZeroIdShouldReturn422() throws Exception {
    mockMvc
        .perform(get("/api/system/employees/role-ids?id=0"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(ExceptionCode.VALIDATION_FAILED.getCode()));
  }

  private String validUpdateJsonWithoutId() {
    return """
    {"username":"zhangsan","name":"张三","departmentId":3,
     "status":"ENABLED","superuser":false}
    """;
  }
}
