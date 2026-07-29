package com.oa.action.controller.system;

import com.oa.common.constant.AuthenticationConstants;
import com.oa.common.context.CurrentEmployeeContext;
import com.oa.common.model.system.dto.LoginDTO;
import com.oa.common.model.system.vo.CurrentEmployeeVO;
import com.oa.common.response.ApiResult;
import com.oa.service.system.AuthService;
import com.oa.service.system.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录会话接口。 */
@Tag(name = "登录认证", description = "管理员登录会话管理")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;
  private final SessionService sessionService;
  private final boolean cookieSecure;

  public AuthController(
      AuthService authService,
      SessionService sessionService,
      @Value("${app.security.cookie-secure:false}") boolean cookieSecure) {
    this.authService = authService;
    this.sessionService = sessionService;
    this.cookieSecure = cookieSecure;
  }

  @Operation(summary = "登录", description = "校验用户名密码并通过 HttpOnly Cookie 建立会话")
  @ApiResponse(responseCode = "200", description = "登录成功")
  @ApiResponse(responseCode = "400", description = "用户名或密码错误")
  @ApiResponse(responseCode = "503", description = "认证服务不可用")
  @PostMapping("/login")
  public ApiResult<Void> login(@Valid @RequestBody LoginDTO login, HttpServletResponse response) {
    String token = authService.login(login);
    response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, -1).toString());
    return ApiResult.success(null);
  }

  @Operation(summary = "退出登录", description = "删除当前 Cookie 对应的服务端会话")
  @ApiResponse(responseCode = "200", description = "退出成功")
  @PostMapping("/logout")
  public ApiResult<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    String token = cookieToken(request);
    sessionService.removeSession(token);
    response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
    return ApiResult.success(null);
  }

  @Operation(summary = "获取当前员工", description = "返回当前有效登录会话中的最小员工信息")
  @ApiResponse(responseCode = "200", description = "获取成功")
  @ApiResponse(responseCode = "401", description = "未登录或会话失效")
  @GetMapping("/current")
  public ApiResult<CurrentEmployeeVO> current() {
    return ApiResult.success(CurrentEmployeeContext.get());
  }

  private ResponseCookie cookie(String value, long maxAge) {
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(AuthenticationConstants.TOKEN_COOKIE_NAME, value)
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/")
            .sameSite("Lax");
    if (maxAge >= 0) {
      builder.maxAge(maxAge);
    }
    return builder.build();
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
}
