package com.oa.boot.security;

import com.oa.common.constant.AuthenticationConstants;
import com.oa.common.context.CurrentEmployeeContext;
import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.cache.LoginSessionCache;
import com.oa.common.model.system.vo.CurrentEmployeeVO;
import com.oa.service.system.PermissionService;
import com.oa.service.system.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 从 HttpOnly Cookie 恢复登录员工的认证过滤器。 */
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {
  private final SessionService sessionService;
  private final PermissionService permissionService;
  private final String frontendOrigin;

  public TokenAuthenticationFilter(
      SessionService sessionService,
      PermissionService permissionService,
      @Value("${app.security.frontend-origin:http://localhost:3000}") String frontendOrigin) {
    this.sessionService = sessionService;
    this.permissionService = permissionService;
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
      writeError(response, 403, ExceptionCode.INVALID_REQUEST_ORIGIN);
      return;
    }
    if (SecurityPathRules.isPublicPath(request.getRequestURI())) {
      filterChain.doFilter(request, response);
      return;
    }
    String token = cookieToken(request);
    if (token == null) {
      writeError(response, 401, ExceptionCode.UNAUTHORIZED);
      return;
    }
    LoginSessionCache session;
    try {
      session = sessionService.getAndRefresh(token);
    } catch (AuthenticationInfrastructureException exception) {
      writeError(response, 503, ExceptionCode.AUTH_INFRASTRUCTURE_UNAVAILABLE);
      return;
    }
    if (session == null) {
      writeError(response, 401, ExceptionCode.UNAUTHORIZED);
      return;
    }
    CurrentEmployeeVO currentEmployee;
    try {
      currentEmployee =
          currentEmployee(session, "/api/auth/current".equals(request.getRequestURI()));
    } catch (AuthenticationInfrastructureException exception) {
      writeError(response, 503, ExceptionCode.AUTH_INFRASTRUCTURE_UNAVAILABLE);
      return;
    }
    CurrentEmployeeContext.set(currentEmployee);
    try {
      filterChain.doFilter(request, response);
    } finally {
      CurrentEmployeeContext.clear();
    }
  }

  private CurrentEmployeeVO currentEmployee(LoginSessionCache session, boolean includeResources) {
    CurrentEmployeeVO employee = new CurrentEmployeeVO();
    employee.setId(session.getEmployeeId());
    employee.setUsername(session.getUsername());
    employee.setName(session.getName());
    employee.setSuperuser(session.getSuperuser());
    employee.setResources(
        includeResources ? permissionService.getResources(session.getEmployeeId()) : List.of());
    return employee;
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

  private void writeError(HttpServletResponse response, int status, ExceptionCode exceptionCode)
      throws IOException {
    response.setStatus(status);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("application/json");
    response
        .getWriter()
        .write(
            "{\"code\":"
                + exceptionCode.getCode()
                + ",\"message\":\""
                + exceptionCode.getName()
                + "\",\"details\":null}");
  }
}
