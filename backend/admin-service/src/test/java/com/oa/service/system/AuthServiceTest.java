package com.oa.service.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.exception.BusinessException;
import com.oa.common.model.system.dto.LoginDTO;
import com.oa.dao.system.EmployeeMapper;
import com.oa.entity.system.EmployeeEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {
  @Mock private EmployeeMapper employeeMapper;
  @Mock private SessionService sessionService;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    authService = new AuthService(employeeMapper, sessionService, new BCryptPasswordEncoder());
  }

  @Test
  void 错误用户名不创建会话() {
    when(employeeMapper.selectByUsername("missing")).thenReturn(null);
    BusinessException exception =
        assertThrows(BusinessException.class, () -> authService.login(login("missing", "wrong")));
    assertEquals("用户名或密码错误", exception.getMessage());
    verify(sessionService, never()).createSession(any());
  }

  @Test
  void 错误密码不创建会话() {
    when(employeeMapper.selectByUsername("user")).thenReturn(employee("correct", 1));
    BusinessException exception =
        assertThrows(BusinessException.class, () -> authService.login(login("user", "wrong")));
    assertEquals("用户名或密码错误", exception.getMessage());
    verify(sessionService, never()).createSession(any());
  }

  @Test
  void 禁用员工不创建会话() {
    when(employeeMapper.selectByUsername("user")).thenReturn(employee("correct", 0));
    BusinessException exception =
        assertThrows(BusinessException.class, () -> authService.login(login("user", "correct")));
    assertEquals("用户名或密码错误", exception.getMessage());
    verify(sessionService, never()).createSession(any());
  }

  @Test
  void 正确凭据创建会话并返回令牌() {
    when(employeeMapper.selectByUsername("user")).thenReturn(employee("correct", 1));
    when(sessionService.createSession(any())).thenReturn("token");
    assertEquals("token", authService.login(login("user", "correct")));
  }

  private LoginDTO login(String username, String password) {
    LoginDTO login = new LoginDTO();
    login.setUsername(username);
    login.setPassword(password);
    return login;
  }

  private EmployeeEntity employee(String password, int status) {
    EmployeeEntity employee = new EmployeeEntity();
    employee.setId(7L);
    employee.setUsername("user");
    employee.setName("测试员工");
    employee.setPasswordHash(new BCryptPasswordEncoder().encode(password));
    employee.setStatus(status);
    employee.setSuperuser(0);
    return employee;
  }
}
