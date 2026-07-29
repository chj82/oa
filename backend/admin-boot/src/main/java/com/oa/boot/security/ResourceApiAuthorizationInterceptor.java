package com.oa.boot.security;

import com.oa.common.context.CurrentEmployeeContext;
import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.vo.CurrentEmployeeVO;
import com.oa.service.system.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/** 根据 Spring MVC 已匹配路由模板执行资源接口授权。 */
@Component
public class ResourceApiAuthorizationInterceptor implements HandlerInterceptor {
  private final PermissionService permissionService;

  public ResourceApiAuthorizationInterceptor(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  /** 校验当前登录员工对已匹配接口模板的访问权限。 */
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    if ("OPTIONS".equals(request.getMethod())) {
      return true;
    }
    CurrentEmployeeVO employee = CurrentEmployeeContext.get();
    if (employee == null) {
      writeError(response, 401, ExceptionCode.UNAUTHORIZED);
      return false;
    }

    Object patternAttribute = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (!(patternAttribute instanceof String apiPath)) {
      writeError(response, 403, ExceptionCode.FORBIDDEN);
      return false;
    }

    try {
      if (permissionService.hasPermission(employee.getId(), apiPath)) {
        return true;
      }
    } catch (AuthenticationInfrastructureException exception) {
      writeError(response, 503, ExceptionCode.AUTH_INFRASTRUCTURE_UNAVAILABLE);
      return false;
    }
    writeError(response, 403, ExceptionCode.FORBIDDEN);
    return false;
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
