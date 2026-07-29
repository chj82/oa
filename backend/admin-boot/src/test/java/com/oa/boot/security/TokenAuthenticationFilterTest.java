package com.oa.boot.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.context.CurrentEmployeeContext;
import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.cache.LoginSessionCache;
import com.oa.common.model.system.vo.CurrentEmployeeVO;
import com.oa.common.model.system.vo.ResourceVO;
import com.oa.service.system.PermissionService;
import com.oa.service.system.SessionService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TokenAuthenticationFilterTest {
  @Mock private SessionService sessionService;
  @Mock private PermissionService permissionService;
  @Mock private FilterChain chain;
  private TokenAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    filter =
        new TokenAuthenticationFilter(sessionService, permissionService, "http://localhost:3000/");
  }

  @AfterEach
  void tearDown() {
    CurrentEmployeeContext.clear();
  }

  @Test
  void 公开GET请求放行且不读取会话() throws Exception {
    MockHttpServletRequest request = request("GET", "/api/auth/login");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(sessionService, never()).getAndRefresh(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void Swagger精确路径和子路径公开放行() throws Exception {
    for (String path :
        new String[] {
          "/swagger-ui.html",
          "/swagger-ui",
          "/swagger-ui/index.html",
          "/v3/api-docs",
          "/v3/api-docs/swagger-config"
        }) {
      MockHttpServletRequest request = request("GET", path);
      filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    verify(chain, org.mockito.Mockito.times(5))
        .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    verify(sessionService, never()).getAndRefresh(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void Swagger相似前缀路径仍需认证() throws Exception {
    for (String path : new String[] {"/swagger-uiAnything", "/v3/api-docsAnything"}) {
      MockHttpServletRequest request = request("GET", path);
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(request, response, chain);
      assertEquals(401, response.getStatus());
    }

    verify(chain, never())
        .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void Cors预检请求放行且不读取会话() throws Exception {
    MockHttpServletRequest request = request("OPTIONS", "/api/auth/current");
    request.addHeader("Origin", "http://localhost:3000");
    request.addHeader("Access-Control-Request-Method", "GET");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    verify(sessionService, never()).getAndRefresh(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void 受保护请求无Cookie返回401且忽略Authorization() throws Exception {
    MockHttpServletRequest request = request("GET", "/api/auth/current");
    request.addHeader("Authorization", "Bearer token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertEquals(401, response.getStatus());
    assertEquals(
        "{\"code\":"
            + ExceptionCode.UNAUTHORIZED.getCode()
            + ",\"message\":\"未登录或登录态已失效\",\"details\":null}",
        response.getContentAsString());
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void 伪造过期或已退出Token统一返回401且不续期() throws Exception {
    when(sessionService.getAndRefresh("invalid-token")).thenReturn(null);
    MockHttpServletRequest request = request("GET", "/api/auth/current");
    request.setCookies(new jakarta.servlet.http.Cookie("ADMIN_TOKEN", "invalid-token"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertEquals(401, response.getStatus());
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void 有效Cookie设置当前员工并在请求结束清理() throws Exception {
    LoginSessionCache session = new LoginSessionCache();
    session.setEmployeeId(7L);
    when(sessionService.getAndRefresh("token")).thenReturn(session);
    ResourceVO resource = new ResourceVO();
    resource.setId(11L);
    when(permissionService.getResources(7L)).thenReturn(java.util.List.of(resource));
    MockHttpServletRequest request = request("GET", "/api/auth/current");
    request.setCookies(new jakarta.servlet.http.Cookie("ADMIN_TOKEN", "token"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              CurrentEmployeeVO employee = CurrentEmployeeContext.get();
              assertEquals(7L, employee.getId());
              assertEquals(11L, employee.getResources().get(0).getId());
              return null;
            })
        .when(chain)
        .doFilter(request, response);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertNull(CurrentEmployeeContext.get());
  }

  @Test
  void 非当前员工接口不加载完整资源树() throws Exception {
    LoginSessionCache session = new LoginSessionCache();
    session.setEmployeeId(7L);
    when(sessionService.getAndRefresh("token")).thenReturn(session);
    MockHttpServletRequest request = request("GET", "/api/system/employees/page");
    request.setCookies(new jakarta.servlet.http.Cookie("ADMIN_TOKEN", "token"));

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    verify(permissionService, never()).getResources(7L);
  }

  @Test
  void Redis不可用返回503且失败关闭() throws Exception {
    when(sessionService.getAndRefresh("token"))
        .thenThrow(new AuthenticationInfrastructureException("redis unavailable"));
    MockHttpServletRequest request = request("GET", "/api/auth/current");
    request.setCookies(new jakarta.servlet.http.Cookie("ADMIN_TOKEN", "token"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertEquals(503, response.getStatus());
    assertEquals(
        "{\"code\":"
            + ExceptionCode.AUTH_INFRASTRUCTURE_UNAVAILABLE.getCode()
            + ",\"message\":\"认证服务暂不可用\",\"details\":null}",
        response.getContentAsString());
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void 当前员工权限缓存不可用返回503且失败关闭() throws Exception {
    LoginSessionCache session = new LoginSessionCache();
    session.setEmployeeId(7L);
    when(sessionService.getAndRefresh("token")).thenReturn(session);
    when(permissionService.getResources(7L))
        .thenThrow(new AuthenticationInfrastructureException("redis unavailable"));
    MockHttpServletRequest request = request("GET", "/api/auth/current");
    request.setCookies(new jakarta.servlet.http.Cookie("ADMIN_TOKEN", "token"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertEquals(503, response.getStatus());
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void 下游数据访问异常不由认证过滤器吞掉且仍清理当前员工() throws Exception {
    LoginSessionCache session = new LoginSessionCache();
    session.setEmployeeId(7L);
    when(sessionService.getAndRefresh("token")).thenReturn(session);
    MockHttpServletRequest request = request("GET", "/api/auth/current");
    request.setCookies(new jakarta.servlet.http.Cookie("ADMIN_TOKEN", "token"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("database unavailable"))
        .when(chain)
        .doFilter(request, response);

    assertThrows(
        DataAccessResourceFailureException.class, () -> filter.doFilter(request, response, chain));
    assertNull(CurrentEmployeeContext.get());
  }

  @Test
  void 非安全请求接受规范化Origin或Referer() throws Exception {
    MockHttpServletRequest originRequest = request("POST", "/api/auth/login");
    originRequest.addHeader("Origin", "http://localhost:3000");
    filter.doFilter(originRequest, new MockHttpServletResponse(), chain);

    MockHttpServletRequest refererRequest = request("POST", "/api/auth/login");
    refererRequest.addHeader("Referer", "http://localhost:3000/login?from=test");
    filter.doFilter(refererRequest, new MockHttpServletResponse(), chain);

    verify(chain, org.mockito.Mockito.times(2))
        .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void 非安全请求来源无法确认返回403() throws Exception {
    for (String origin : new String[] {null, "http://evil.example", "http://localhost:3000.evil"}) {
      MockHttpServletRequest request = request("POST", "/api/auth/login");
      if (origin != null) request.addHeader("Origin", origin);
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(request, response, chain);
      assertEquals(403, response.getStatus());
    }
  }

  @Test
  void 前端来源配置拒绝危险格式() {
    for (String origin :
        new String[] {
          "*",
          "http://a.example,http://b.example",
          "http://user@a.example",
          "http://a.example/x",
          "http://a.example?x=1",
          "http://a.example/#x"
        }) {
      assertThrows(
          IllegalArgumentException.class,
          () -> new TokenAuthenticationFilter(sessionService, permissionService, origin));
    }
  }

  private MockHttpServletRequest request(String method, String path) {
    return new MockHttpServletRequest(method, path);
  }
}
