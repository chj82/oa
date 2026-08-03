package com.oa.boot.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.context.CurrentEmployeeContext;
import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.vo.CurrentEmployeeVO;
import com.oa.service.system.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

/** 资源接口授权拦截器测试。 */
class ResourceApiAuthorizationInterceptorTest {
  @Mock private PermissionService permissionService;
  private ResourceApiAuthorizationInterceptor interceptor;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    interceptor = new ResourceApiAuthorizationInterceptor(permissionService, new ObjectMapper());
  }

  @AfterEach
  void tearDown() {
    CurrentEmployeeContext.clear();
  }

  /** 拦截器直接执行且缺少认证上下文时返回 401。 */
  @Test
  void 缺少登录员工返回401() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertFalse(
        interceptor.preHandle(request("/api/orders/7", "/api/orders/{id}", null), response, this));

    assertEquals(401, response.getStatus());
    assertCode(response, ExceptionCode.UNAUTHORIZED);
    verify(permissionService, never())
        .hasPermission(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
  }

  /** CORS 预检请求不需要登录上下文，由 MVC 的 CORS 处理继续响应。 */
  @Test
  void Cors预检请求直接放行() throws Exception {
    MockHttpServletRequest request = request("/api/orders", "/api/orders", null);
    request.setMethod("OPTIONS");

    assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), this));

    verify(permissionService, never())
        .hasPermission(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
  }

  /** 未登记、已禁用或没有资源关联的接口统一返回 403。 */
  @Test
  void 无接口权限返回403() throws Exception {
    CurrentEmployeeVO employee = employee(false);
    when(permissionService.hasPermission(7L, "/api/orders/{id}")).thenReturn(false);
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertFalse(
        interceptor.preHandle(
            request("/api/orders/7", "/api/orders/{id}", employee), response, this));

    assertEquals(403, response.getStatus());
    assertCode(response, ExceptionCode.FORBIDDEN);
  }

  /** 普通员工按已匹配路由模板查询缓存权限。 */
  @Test
  void 普通员工按已匹配模板鉴权() throws Exception {
    CurrentEmployeeVO employee = employee(false);
    when(permissionService.hasPermission(7L, "/api/orders/{id}")).thenReturn(true);

    assertTrue(
        interceptor.preHandle(
            request("/api/orders/999", "/api/orders/{id}", employee),
            new MockHttpServletResponse(),
            this));

    verify(permissionService).hasPermission(7L, "/api/orders/{id}");
  }

  /** 超级管理员也通过启用接口路径缓存判断，不能直接绕过禁用接口。 */
  @Test
  void 超级管理员不能绕过接口禁用() throws Exception {
    CurrentEmployeeVO employee = employee(true);
    when(permissionService.hasPermission(7L, "/api/enabled")).thenReturn(true);
    when(permissionService.hasPermission(7L, "/api/disabled")).thenReturn(false);

    assertTrue(
        interceptor.preHandle(
            request("/api/enabled", "/api/enabled", employee),
            new MockHttpServletResponse(),
            this));
    MockHttpServletResponse disabledResponse = new MockHttpServletResponse();
    assertFalse(
        interceptor.preHandle(
            request("/api/disabled", "/api/disabled", employee), disabledResponse, this));

    assertEquals(403, disabledResponse.getStatus());
    verify(permissionService).hasPermission(7L, "/api/disabled");
  }

  /** Redis 权限缓存不可用时失败关闭并返回 503，不伪装成无权限。 */
  @Test
  void Redis不可用返回503() throws Exception {
    CurrentEmployeeVO employee = employee(false);
    when(permissionService.hasPermission(7L, "/api/orders"))
        .thenThrow(new AuthenticationInfrastructureException("认证服务暂不可用"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertFalse(
        interceptor.preHandle(request("/api/orders", "/api/orders", employee), response, this));

    assertEquals(503, response.getStatus());
    assertCode(response, ExceptionCode.AUTH_INFRASTRUCTURE_UNAVAILABLE);
  }

  private void assertCode(MockHttpServletResponse response, ExceptionCode exceptionCode)
      throws Exception {
    JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
    assertFalse(body.get("success").asBoolean());
    assertEquals(exceptionCode.getCode(), body.get("code").asInt());
    assertEquals(exceptionCode.getName(), body.get("message").asText());
    assertTrue(body.get("details").isNull());
  }

  private MockHttpServletRequest request(
      String requestUri, String matchingPattern, CurrentEmployeeVO employee) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, matchingPattern);
    if (employee != null) {
      CurrentEmployeeContext.set(employee);
    }
    return request;
  }

  private CurrentEmployeeVO employee(boolean superuser) {
    CurrentEmployeeVO employee = new CurrentEmployeeVO();
    employee.setId(7L);
    employee.setSuperuser(superuser);
    return employee;
  }
}
