package com.oa.boot.security;

import com.oa.common.constant.AuthenticationConstants;
import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.system.cache.LoginSessionCache;
import com.oa.common.model.system.vo.CurrentEmployeeVO;
import com.oa.service.system.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 从 HttpOnly Cookie 恢复登录员工的认证过滤器。 */
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {
  private static final Set<String> PUBLIC_PATHS = Set.of("/api/auth/login", "/error");

  private final SessionService sessionService;
  private final String frontendOrigin;

  public TokenAuthenticationFilter(
      SessionService sessionService,
      @Value("${app.security.frontend-origin:http://localhost:3000}") String frontendOrigin) {
    this.sessionService = sessionService;
    this.frontendOrigin = FrontendOrigin.normalize(frontendOrigin);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if ("OPTIONS".equals(request.getMethod())) {
      filterChain.doFilter(request, response);
      return;
    }
    if (requiresOriginCheck(request) && !hasTrustedOrigin(request)) {
      writeError(response, 403, "INVALID_REQUEST_ORIGIN", "请求来源不可信");
      return;
    }
    if (isPublicPath(request.getRequestURI())) {
      filterChain.doFilter(request, response);
      return;
    }
    String token = cookieToken(request);
    if (token == null) {
      writeError(response, 401, "UNAUTHORIZED", "未登录或登录态已失效");
      return;
    }
    LoginSessionCache session;
    try {
      session = sessionService.getAndRefresh(token);
    } catch (AuthenticationInfrastructureException exception) {
      writeError(response, 503, "AUTH_INFRASTRUCTURE_UNAVAILABLE", "认证服务暂不可用");
      return;
    }
    if (session == null) {
      writeError(response, 401, "UNAUTHORIZED", "未登录或登录态已失效");
      return;
    }
    request.setAttribute(
        AuthenticationConstants.CURRENT_EMPLOYEE_ATTRIBUTE, currentEmployee(session));
    try {
      filterChain.doFilter(request, response);
    } finally {
      request.removeAttribute(AuthenticationConstants.CURRENT_EMPLOYEE_ATTRIBUTE);
    }
  }

  private CurrentEmployeeVO currentEmployee(LoginSessionCache session) {
    CurrentEmployeeVO employee = new CurrentEmployeeVO();
    employee.setId(session.getEmployeeId());
    employee.setUsername(session.getUsername());
    employee.setName(session.getName());
    employee.setSuperuser(session.getSuperuser());
    employee.setResources(List.of());
    return employee;
  }

  private boolean isPublicPath(String path) {
    return PUBLIC_PATHS.contains(path)
        || "/swagger-ui.html".equals(path)
        || "/swagger-ui".equals(path)
        || path.startsWith("/swagger-ui/")
        || "/v3/api-docs".equals(path)
        || path.startsWith("/v3/api-docs/");
  }

  private boolean requiresOriginCheck(HttpServletRequest request) {
    return !("GET".equals(request.getMethod())
        || "HEAD".equals(request.getMethod())
        || "OPTIONS".equals(request.getMethod()));
  }

  private boolean hasTrustedOrigin(HttpServletRequest request) {
    String origin = request.getHeader("Origin");
    if (origin != null) {
      return frontendOrigin.equals(origin);
    }
    return frontendOrigin.equals(FrontendOrigin.fromReferer(request.getHeader("Referer")));
  }

  private String cookieToken(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return null;
    }
    for (Cookie cookie : request.getCookies()) {
      if (AuthenticationConstants.TOKEN_COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  private void writeError(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("application/json");
    response
        .getWriter()
        .write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"details\":null}");
  }
}
