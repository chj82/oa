package com.oa.service.system;

import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.cache.LoginSessionCache;
import com.oa.common.model.system.dto.LoginDTO;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.dao.system.EmployeeMapper;
import com.oa.entity.system.EmployeeEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** 员工登录服务。 */
@Service
public class AuthService {
  private final EmployeeMapper employeeMapper;
  private final SessionService sessionService;
  private final BCryptPasswordEncoder passwordEncoder;

  public AuthService(
      EmployeeMapper employeeMapper,
      SessionService sessionService,
      BCryptPasswordEncoder passwordEncoder) {
    this.employeeMapper = employeeMapper;
    this.sessionService = sessionService;
    this.passwordEncoder = passwordEncoder;
  }

  /** 校验登录凭据并创建会话。 */
  public String login(LoginDTO login) {
    EmployeeEntity employee = employeeMapper.selectByUsername(login.getUsername());
    if (employee == null
        || employee.getStatus() != SystemStatus.ENABLED.getCode()
        || !passwordEncoder.matches(login.getPassword(), employee.getPasswordHash())) {
      throw new BusinessException(ExceptionCode.INVALID_CREDENTIALS);
    }
    LoginSessionCache session = new LoginSessionCache();
    session.setEmployeeId(employee.getId());
    session.setUsername(employee.getUsername());
    session.setName(employee.getName());
    session.setSuperuser(employee.getSuperuser() == 1);
    return sessionService.createSession(session);
  }
}
