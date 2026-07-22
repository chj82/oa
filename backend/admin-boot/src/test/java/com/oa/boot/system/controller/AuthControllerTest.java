package com.oa.boot.system.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oa.action.controller.system.AuthController;
import com.oa.boot.advice.GlobalExceptionHandler;
import com.oa.common.constant.AuthenticationConstants;
import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.exception.BusinessException;
import com.oa.common.model.system.dto.LoginDTO;
import com.oa.common.model.system.vo.CurrentEmployeeVO;
import com.oa.service.system.AuthService;
import com.oa.service.system.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {
  @Mock private AuthService authService;
  @Mock private SessionService sessionService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AuthController(authService, sessionService, false))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void 登录只下发安全Cookie且正文不返回令牌和员工() throws Exception {
    when(authService.login(org.mockito.ArgumentMatchers.any(LoginDTO.class)))
        .thenReturn("secret-token");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"user\",\"password\":\"password\"}"))
        .andExpect(status().isOk())
        .andExpect(cookie().value("ADMIN_TOKEN", "secret-token"))
        .andExpect(cookie().httpOnly("ADMIN_TOKEN", true))
        .andExpect(cookie().path("ADMIN_TOKEN", "/"))
        .andExpect(cookie().secure("ADMIN_TOKEN", false))
        .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.details").value(nullValue()))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("secret-token"))));
  }

  @Test
  void 登录时Redis不可用返回503且不下发Cookie() throws Exception {
    when(authService.login(org.mockito.ArgumentMatchers.any(LoginDTO.class)))
        .thenThrow(new AuthenticationInfrastructureException("redis unavailable"));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"user\",\"password\":\"password\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(cookie().doesNotExist("ADMIN_TOKEN"))
        .andExpect(jsonPath("$.code").value("AUTH_INFRASTRUCTURE_UNAVAILABLE"));
  }

  @Test
  void 错误凭据返回400统一业务响应() throws Exception {
    when(authService.login(org.mockito.ArgumentMatchers.any(LoginDTO.class)))
        .thenThrow(new BusinessException("INVALID_CREDENTIALS", "用户名或密码错误"));

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"user\",\"password\":\"wrong\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.message").value("用户名或密码错误"))
        .andExpect(jsonPath("$.details").value(nullValue()));
  }

  @Test
  void 登录请求字段校验失败返回422() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 退出时Redis不可用返回503() throws Exception {
    org.mockito.Mockito.doThrow(new AuthenticationInfrastructureException("redis unavailable"))
        .when(sessionService)
        .removeSession("token");

    mockMvc
        .perform(
            post("/api/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie("ADMIN_TOKEN", "token")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AUTH_INFRASTRUCTURE_UNAVAILABLE"));
  }

  @Test
  void 非本地配置登录Cookie启用Secure() throws Exception {
    MockMvc secureMockMvc =
        MockMvcBuilders.standaloneSetup(new AuthController(authService, sessionService, true))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    when(authService.login(org.mockito.ArgumentMatchers.any(LoginDTO.class))).thenReturn("token");

    secureMockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"user\",\"password\":\"password\"}"))
        .andExpect(status().isOk())
        .andExpect(cookie().secure("ADMIN_TOKEN", true));
  }

  @Test
  void 退出删除当前会话并清除Cookie() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie("ADMIN_TOKEN", "token")))
        .andExpect(status().isOk())
        .andExpect(cookie().maxAge("ADMIN_TOKEN", 0));

    verify(sessionService).removeSession("token");
  }

  @Test
  void 当前员工接口返回请求属性() throws Exception {
    CurrentEmployeeVO session = new CurrentEmployeeVO();
    session.setId(7L);
    session.setUsername("user");

    mockMvc
        .perform(
            get("/api/auth/current")
                .requestAttr(AuthenticationConstants.CURRENT_EMPLOYEE_ATTRIBUTE, session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.details.id").value(7L));
  }

  @Test
  void 普通数据访问异常返回通用基础设施错误码() {
    org.springframework.http.ResponseEntity<com.oa.common.response.ApiResponse<Void>> response =
        new GlobalExceptionHandler()
            .handleInfrastructure(new DataAccessResourceFailureException("database unavailable"));

    org.junit.jupiter.api.Assertions.assertEquals(503, response.getStatusCode().value());
    org.junit.jupiter.api.Assertions.assertEquals(
        "INFRASTRUCTURE_UNAVAILABLE", response.getBody().getCode());
  }
}
