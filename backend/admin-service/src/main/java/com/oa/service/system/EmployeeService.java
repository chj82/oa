package com.oa.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.EmployeeCreateDTO;
import com.oa.common.model.system.dto.EmployeeQueryDTO;
import com.oa.common.model.system.dto.EmployeeUpdateDTO;
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
import com.oa.entity.system.EmployeeRoleEntity;
import com.oa.entity.system.RoleEntity;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 员工管理服务。 */
@Service
public class EmployeeService {
  private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
  private static final int RELATION_BATCH_SIZE = 500;
  private static final String USERNAME_UNIQUE_INDEX = "udx_employee_username";
  private static final String PHONE_UNIQUE_INDEX = "udx_employee_phone";
  private static final String EMAIL_UNIQUE_INDEX = "udx_employee_email";

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

  /** 查询员工已分配的角色ID。 */
  public List<Long> roleIds(long employeeId) {
    requiredEmployee(employeeId);
    return employeeRoleMapper.selectRoleIdsByEmployeeId(employeeId);
  }

  /** 新增员工。 */
  @Transactional
  public EmployeeVO create(EmployeeCreateDTO request) {
    if (isBlank(request.getPassword())) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_PASSWORD_REQUIRED);
    }
    validatePasswordLength(request.getPassword());
    lockAvailableDepartment(request.getDepartmentId());
    validateUnique(request.getUsername(), request.getPhone(), request.getEmail(), null);
    LocalDateTime now = LocalDateTime.now();
    EmployeeEntity employee = new EmployeeEntity();
    copyEditableFields(
        request.getUsername(),
        request.getName(),
        request.getPhone(),
        request.getEmail(),
        request.getDepartmentId(),
        request.getStatus(),
        request.getSuperuser(),
        employee);
    employee.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    employee.setCreatedAt(now);
    employee.setUpdatedAt(now);
    insertEmployee(employee);
    return toVO(employee);
  }

  /** 修改员工基本信息。 */
  @Transactional
  public EmployeeVO update(EmployeeUpdateDTO request, CurrentEmployeeVO currentEmployee) {
    EmployeeEntity employee = requiredEmployee(request.getId());
    boolean superuserChanged = employee.getSuperuser() != (request.getSuperuser() ? 1 : 0);
    boolean statusChanged = employee.getStatus() != request.getStatus().getCode();
    int expectedStatus = employee.getStatus();
    int expectedSuperuser = employee.getSuperuser();
    if (isSelfNormalEmployee(employee.getId(), currentEmployee)
        && (superuserChanged || statusChanged)) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_SELF_OPERATION_FORBIDDEN);
    }
    if (employee.getSuperuser() == 1 && request.getStatus() == SystemStatus.DISABLED) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_SUPERUSER_PROTECTED);
    }
    if (employee.getSuperuser() == 1 && !request.getSuperuser()) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_SUPERUSER_DEMOTION_FORBIDDEN);
    }
    if (superuserChanged || statusChanged) {
      permissionService.invalidateAll();
    }
    lockAvailableDepartment(request.getDepartmentId());
    validateUnique(request.getUsername(), request.getPhone(), request.getEmail(), employee.getId());
    copyEditableFields(
        request.getUsername(),
        request.getName(),
        request.getPhone(),
        request.getEmail(),
        request.getDepartmentId(),
        request.getStatus(),
        request.getSuperuser(),
        employee);
    employee.setUpdatedAt(LocalDateTime.now());
    updateEmployeeBySnapshot(employee, expectedStatus, expectedSuperuser);
    if (superuserChanged || (statusChanged && request.getStatus() == SystemStatus.DISABLED)) {
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
      requireCurrentSnapshot(employee);
      return;
    }
    int expectedStatus = employee.getStatus();
    int expectedSuperuser = employee.getSuperuser();
    employee.setStatus(status.getCode());
    employee.setUpdatedAt(LocalDateTime.now());
    permissionService.invalidateAll();
    updateEmployeeBySnapshot(employee, expectedStatus, expectedSuperuser);
    if (status == SystemStatus.DISABLED) {
      invalidateSessionsAfterCommit(employeeId);
    }
  }

  /** 删除员工及其角色关联。 */
  @Transactional
  public void delete(long employeeId, long operatorId) {
    if (employeeId == operatorId) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_SELF_DELETE_FORBIDDEN);
    }
    permissionService.invalidateAll();
    EmployeeEntity employee = employeeMapper.selectByIdForUpdate(employeeId);
    if (employee == null) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_NOT_FOUND);
    }
    if (employee.getSuperuser() == 1) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_SUPERUSER_PROTECTED);
    }
    employeeRoleMapper.deleteByEmployeeId(employeeId);
    employeeMapper.deleteById(employeeId);
    invalidateSessionsAfterCommit(employeeId);
  }

  /** 重置员工登录密码。 */
  @Transactional
  public void resetPassword(long employeeId, String password) {
    validatePasswordLength(password);
    EmployeeEntity employee = requiredEmployee(employeeId);
    employee.setPasswordHash(passwordEncoder.encode(password));
    employee.setUpdatedAt(LocalDateTime.now());
    updateEmployee(employee);
    invalidateSessionsAfterCommit(employeeId);
  }

  /** 全量保存员工角色，空列表表示清空。 */
  @Transactional
  public void saveRoles(long employeeId, List<Long> roleIds, CurrentEmployeeVO currentEmployee) {
    if (isSelfNormalEmployee(employeeId, currentEmployee)) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_SELF_OPERATION_FORBIDDEN);
    }
    List<Long> distinctRoleIds = new LinkedHashSet<>(roleIds).stream().sorted().toList();
    permissionService.invalidateAll();
    if (employeeMapper.selectByIdForUpdate(employeeId) == null) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_NOT_FOUND);
    }
    List<RoleEntity> lockedRoles =
        distinctRoleIds.isEmpty() ? List.of() : roleMapper.selectByIdsForUpdate(distinctRoleIds);
    Set<Long> enabledRoleIds =
        lockedRoles.stream()
            .filter(role -> role.getStatus() == SystemStatus.ENABLED.getCode())
            .map(RoleEntity::getId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    if (!enabledRoleIds.equals(new LinkedHashSet<>(distinctRoleIds))) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_ROLE_UNAVAILABLE);
    }
    employeeRoleMapper.deleteByEmployeeId(employeeId);
    insertEmployeeRoleRelations(employeeId, distinctRoleIds);
  }

  private void lockAvailableDepartment(long departmentId) {
    DepartmentEntity department = departmentMapper.selectByIdForUpdate(departmentId);
    if (department == null || department.getStatus() != SystemStatus.ENABLED.getCode()) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_DEPARTMENT_UNAVAILABLE);
    }
  }

  private void insertEmployeeRoleRelations(long employeeId, List<Long> roleIds) {
    if (roleIds.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    for (int start = 0; start < roleIds.size(); start += RELATION_BATCH_SIZE) {
      int end = Math.min(start + RELATION_BATCH_SIZE, roleIds.size());
      List<EmployeeRoleEntity> relations =
          roleIds.subList(start, end).stream()
              .map(roleId -> employeeRole(employeeId, roleId, now))
              .toList();
      employeeRoleMapper.insertBatch(relations);
    }
  }

  private EmployeeRoleEntity employeeRole(long employeeId, long roleId, LocalDateTime createdAt) {
    EmployeeRoleEntity relation = new EmployeeRoleEntity();
    relation.setEmployeeId(employeeId);
    relation.setRoleId(roleId);
    relation.setCreatedAt(createdAt);
    return relation;
  }

  private void validateUnique(
      String username, String phoneValue, String emailValue, Long employeeId) {
    rejectDuplicate(
        employeeMapper.selectByUsername(username),
        employeeId,
        ExceptionCode.EMPLOYEE_USERNAME_DUPLICATED);
    String phone = nullableText(phoneValue);
    if (phone != null) {
      rejectDuplicate(
          employeeMapper.selectByPhone(phone), employeeId, ExceptionCode.EMPLOYEE_PHONE_DUPLICATED);
    }
    String email = nullableText(emailValue);
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

  private void insertEmployee(EmployeeEntity employee) {
    try {
      employeeMapper.insert(employee);
    } catch (DuplicateKeyException exception) {
      throw translateDuplicateKey(exception);
    }
  }

  private void updateEmployee(EmployeeEntity employee) {
    try {
      employeeMapper.updateById(employee);
    } catch (DuplicateKeyException exception) {
      throw translateDuplicateKey(exception);
    }
  }

  private void updateEmployeeBySnapshot(
      EmployeeEntity employee, int expectedStatus, int expectedSuperuser) {
    try {
      if (employeeMapper.updateBySnapshot(employee, expectedStatus, expectedSuperuser) == 0) {
        throw new BusinessException(ExceptionCode.EMPLOYEE_CONCURRENT_MODIFICATION);
      }
    } catch (DuplicateKeyException exception) {
      throw translateDuplicateKey(exception);
    }
  }

  private void requireCurrentSnapshot(EmployeeEntity employee) {
    if (!employeeMapper.matchesSnapshot(
        employee.getId(), employee.getStatus(), employee.getSuperuser())) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_CONCURRENT_MODIFICATION);
    }
  }

  private RuntimeException translateDuplicateKey(DuplicateKeyException exception) {
    String message = exception.getMessage();
    if (message != null && message.contains(USERNAME_UNIQUE_INDEX)) {
      return new BusinessException(ExceptionCode.EMPLOYEE_USERNAME_DUPLICATED);
    }
    if (message != null && message.contains(PHONE_UNIQUE_INDEX)) {
      return new BusinessException(ExceptionCode.EMPLOYEE_PHONE_DUPLICATED);
    }
    if (message != null && message.contains(EMAIL_UNIQUE_INDEX)) {
      return new BusinessException(ExceptionCode.EMPLOYEE_EMAIL_DUPLICATED);
    }
    return exception;
  }

  private void validatePasswordLength(String password) {
    if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
      throw new BusinessException(ExceptionCode.EMPLOYEE_PASSWORD_TOO_LONG);
    }
  }

  private void copyEditableFields(
      String username,
      String name,
      String phone,
      String email,
      long departmentId,
      SystemStatus status,
      boolean superuser,
      EmployeeEntity employee) {
    employee.setUsername(username);
    employee.setName(name);
    employee.setPhone(nullableText(phone));
    employee.setEmail(nullableText(email));
    employee.setDepartmentId(departmentId);
    employee.setStatus(status.getCode());
    employee.setSuperuser(superuser ? 1 : 0);
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
