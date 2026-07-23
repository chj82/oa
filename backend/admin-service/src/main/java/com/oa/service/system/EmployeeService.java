package com.oa.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.EmployeeQueryDTO;
import com.oa.common.model.system.dto.EmployeeSaveDTO;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.model.system.vo.CurrentEmployeeVO;
import com.oa.common.model.system.vo.EmployeeVO;
import com.oa.common.response.PageResult;
import com.oa.dao.system.DepartmentMapper;
import com.oa.dao.system.EmployeeMapper;
import com.oa.dao.system.EmployeeRoleMapper;
import com.oa.dao.system.RoleMapper;
import com.oa.entity.system.DepartmentEntity;
import com.oa.entity.system.EmployeeEntity;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 员工管理服务。 */
@Service
public class EmployeeService {
  private final EmployeeMapper employeeMapper;
  private final DepartmentMapper departmentMapper;
  private final EmployeeRoleMapper employeeRoleMapper;
  private final RoleMapper roleMapper;
  private final PasswordEncoder passwordEncoder;
  private final SessionService sessionService;
  private final PermissionService permissionService;

  public EmployeeService(
      EmployeeMapper employeeMapper,
      DepartmentMapper departmentMapper,
      EmployeeRoleMapper employeeRoleMapper,
      RoleMapper roleMapper,
      PasswordEncoder passwordEncoder,
      SessionService sessionService,
      PermissionService permissionService) {
    this.employeeMapper = employeeMapper;
    this.departmentMapper = departmentMapper;
    this.employeeRoleMapper = employeeRoleMapper;
    this.roleMapper = roleMapper;
    this.passwordEncoder = passwordEncoder;
    this.sessionService = sessionService;
    this.permissionService = permissionService;
  }

  /** 分页查询员工。 */
  public PageResult<EmployeeVO> page(EmployeeQueryDTO query) {
    IPage<EmployeeEntity> entityPage = employeeMapper.selectEmployeePage(query);
    PageResult<EmployeeVO> result = new PageResult<>();
    result.setRecords(entityPage.getRecords().stream().map(this::toVO).toList());
    result.setTotal(entityPage.getTotal());
    result.setPage(entityPage.getCurrent());
    result.setSize(entityPage.getSize());
    return result;
  }

  /** 查询员工详情。 */
  public EmployeeVO detail(long employeeId) {
    return toVO(requiredEmployee(employeeId));
  }

  /** 新增员工。 */
  @Transactional
  public EmployeeVO create(EmployeeSaveDTO request) {
    if (isBlank(request.getPassword())) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_PASSWORD_REQUIRED);
    }
    validateDepartment(request.getDepartmentId());
    validateUnique(request, null);
    LocalDateTime now = LocalDateTime.now();
    EmployeeEntity employee = new EmployeeEntity();
    copyEditableFields(request, employee);
    employee.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    employee.setCreatedAt(now);
    employee.setUpdatedAt(now);
    employeeMapper.insert(employee);
    if (employee.getStatus() == SystemStatus.DISABLED.getCode() || employee.getSuperuser() == 1) {
      permissionService.invalidateAll();
    }
    return toVO(employee);
  }

  /** 修改员工基本信息。 */
  @Transactional
  public EmployeeVO update(EmployeeSaveDTO request, CurrentEmployeeVO currentEmployee) {
    EmployeeEntity employee = requiredEmployee(request.getId());
    boolean superuserChanged = employee.getSuperuser() != (request.getSuperuser() ? 1 : 0);
    if (isSelfNormalEmployee(employee.getId(), currentEmployee) && superuserChanged) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_SELF_OPERATION_FORBIDDEN);
    }
    validateDepartment(request.getDepartmentId());
    validateUnique(request, employee.getId());
    copyEditableFields(request, employee);
    if (!isBlank(request.getPassword())) {
      employee.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    }
    employee.setUpdatedAt(LocalDateTime.now());
    employeeMapper.updateById(employee);
    if (superuserChanged) {
      permissionService.invalidateAll();
      invalidateSessionsAfterCommit(employee.getId());
    }
    return toVO(employee);
  }

  /** 修改员工启用状态。 */
  @Transactional
  public void changeStatus(
      long employeeId, SystemStatus status, CurrentEmployeeVO currentEmployee) {
    EmployeeEntity employee = requiredEmployee(employeeId);
    if (isSelfNormalEmployee(employeeId, currentEmployee)) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_SELF_OPERATION_FORBIDDEN);
    }
    if (employee.getSuperuser() == 1 && status == SystemStatus.DISABLED) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_SUPERUSER_PROTECTED);
    }
    if (employee.getStatus() == status.getCode()) {
      return;
    }
    employee.setStatus(status.getCode());
    employee.setUpdatedAt(LocalDateTime.now());
    employeeMapper.updateById(employee);
    permissionService.invalidateAll();
    if (status == SystemStatus.DISABLED) {
      invalidateSessionsAfterCommit(employeeId);
    }
  }

  /** 删除员工及其角色关联。 */
  @Transactional
  public void delete(long employeeId) {
    EmployeeEntity employee = requiredEmployee(employeeId);
    if (employee.getSuperuser() == 1) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_SUPERUSER_PROTECTED);
    }
    employeeRoleMapper.deleteByEmployeeId(employeeId);
    employeeMapper.deleteById(employeeId);
    permissionService.invalidateAll();
    invalidateSessionsAfterCommit(employeeId);
  }

  /** 重置员工登录密码。 */
  @Transactional
  public void resetPassword(long employeeId, String password) {
    EmployeeEntity employee = requiredEmployee(employeeId);
    employee.setPasswordHash(passwordEncoder.encode(password));
    employee.setUpdatedAt(LocalDateTime.now());
    employeeMapper.updateById(employee);
    invalidateSessionsAfterCommit(employeeId);
  }

  /** 全量保存员工角色，空列表表示清空。 */
  @Transactional
  public void saveRoles(long employeeId, List<Long> roleIds, CurrentEmployeeVO currentEmployee) {
    requiredEmployee(employeeId);
    if (isSelfNormalEmployee(employeeId, currentEmployee)) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_SELF_OPERATION_FORBIDDEN);
    }
    List<Long> distinctRoleIds = new LinkedHashSet<>(roleIds).stream().toList();
    Set<Long> enabledRoleIds = new LinkedHashSet<>(roleMapper.selectEnabledIds(distinctRoleIds));
    if (!enabledRoleIds.equals(new LinkedHashSet<>(distinctRoleIds))) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_ROLE_UNAVAILABLE);
    }
    employeeRoleMapper.deleteByEmployeeId(employeeId);
    employeeRoleMapper.insertRelations(employeeId, distinctRoleIds, LocalDateTime.now());
    permissionService.invalidateAll();
  }

  private void validateDepartment(long departmentId) {
    DepartmentEntity department = departmentMapper.selectById(departmentId);
    if (department == null || department.getStatus() != SystemStatus.ENABLED.getCode()) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_DEPARTMENT_UNAVAILABLE);
    }
  }

  private void validateUnique(EmployeeSaveDTO request, Long employeeId) {
    rejectDuplicate(
        employeeMapper.selectByUsername(request.getUsername()),
        employeeId,
        ExceptionCode.EMPLOYEE_USERNAME_DUPLICATED);
    String phone = nullableText(request.getPhone());
    if (phone != null) {
      rejectDuplicate(
          employeeMapper.selectByPhone(phone), employeeId, ExceptionCode.EMPLOYEE_PHONE_DUPLICATED);
    }
    String email = nullableText(request.getEmail());
    if (email != null) {
      rejectDuplicate(
          employeeMapper.selectByEmail(email), employeeId, ExceptionCode.EMPLOYEE_EMAIL_DUPLICATED);
    }
  }

  private void rejectDuplicate(
      EmployeeEntity duplicate, Long employeeId, ExceptionCode exceptionCode) {
    if (duplicate != null && !Objects.equals(duplicate.getId(), employeeId)) {
      throw new BusinessException(exceptionCode);
    }
  }

  private void copyEditableFields(EmployeeSaveDTO request, EmployeeEntity employee) {
    employee.setUsername(request.getUsername());
    employee.setName(request.getName());
    employee.setPhone(nullableText(request.getPhone()));
    employee.setEmail(nullableText(request.getEmail()));
    employee.setDepartmentId(request.getDepartmentId());
    employee.setStatus(request.getStatus().getCode());
    employee.setSuperuser(request.getSuperuser() ? 1 : 0);
  }

  private EmployeeEntity requiredEmployee(long employeeId) {
    EmployeeEntity employee = employeeMapper.selectById(employeeId);
    if (employee == null) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_NOT_FOUND);
    }
    return employee;
  }

  private boolean isSelfNormalEmployee(long employeeId, CurrentEmployeeVO currentEmployee) {
    return currentEmployee != null
        && !currentEmployee.getSuperuser()
        && Objects.equals(currentEmployee.getId(), employeeId);
  }

  private String nullableText(String value) {
    return isBlank(value) ? null : value.strip();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private EmployeeVO toVO(EmployeeEntity employee) {
    EmployeeVO result = new EmployeeVO();
    result.setId(employee.getId());
    result.setUsername(employee.getUsername());
    result.setName(employee.getName());
    result.setPhone(employee.getPhone());
    result.setEmail(employee.getEmail());
    result.setDepartmentId(employee.getDepartmentId());
    result.setStatus(
        employee.getStatus() == SystemStatus.ENABLED.getCode()
            ? SystemStatus.ENABLED
            : SystemStatus.DISABLED);
    result.setSuperuser(employee.getSuperuser() == 1);
    result.setCreatedAt(employee.getCreatedAt());
    result.setUpdatedAt(employee.getUpdatedAt());
    return result;
  }

  private void invalidateSessionsAfterCommit(long employeeId) {
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              sessionService.invalidateEmployeeSessions(employeeId);
            }
          });
      return;
    }
    sessionService.invalidateEmployeeSessions(employeeId);
  }
}
