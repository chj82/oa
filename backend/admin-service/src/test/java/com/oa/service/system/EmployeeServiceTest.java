package com.oa.service.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.EmployeeQueryDTO;
import com.oa.common.model.system.dto.EmployeeSaveDTO;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.model.system.vo.CurrentEmployeeVO;
import com.oa.common.response.PageResult;
import com.oa.dao.system.DepartmentMapper;
import com.oa.dao.system.EmployeeMapper;
import com.oa.dao.system.EmployeeRoleMapper;
import com.oa.dao.system.RoleMapper;
import com.oa.entity.system.DepartmentEntity;
import com.oa.entity.system.EmployeeEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 员工管理服务测试。 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {
  @Mock private EmployeeMapper employeeMapper;
  @Mock private DepartmentMapper departmentMapper;
  @Mock private EmployeeRoleMapper employeeRoleMapper;
  @Mock private RoleMapper roleMapper;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private SessionService sessionService;
  @Mock private PermissionService permissionService;

  private EmployeeService employeeService;

  @BeforeEach
  void setUp() {
    employeeService =
        new EmployeeService(
            employeeMapper,
            departmentMapper,
            employeeRoleMapper,
            roleMapper,
            passwordEncoder,
            sessionService,
            permissionService);
  }

  /** 分页查询应由 Mapper 封装查询条件并转换展示模型。 */
  @Test
  void shouldDelegatePageQueryToMapper() {
    EmployeeQueryDTO query = new EmployeeQueryDTO();
    query.setPage(2);
    query.setSize(10);
    EmployeeEntity employee = employee(8L, 0, SystemStatus.ENABLED.getCode());
    IPage<EmployeeEntity> page = new Page<EmployeeEntity>(2, 10, 21).setRecords(List.of(employee));
    when(employeeMapper.selectEmployeePage(query)).thenReturn(page);

    PageResult<?> result = employeeService.page(query);

    assertThat(result.getTotal()).isEqualTo(21);
    assertThat(result.getPage()).isEqualTo(2);
    assertThat(result.getSize()).isEqualTo(10);
    assertThat(result.getRecords()).hasSize(1);
    verify(employeeMapper).selectEmployeePage(query);
  }

  /** 新增员工应验证部门、编码密码并把空联系方式写为NULL。 */
  @Test
  void shouldCreateEmployeeWithEncodedPasswordAndNullContacts() {
    EmployeeSaveDTO request = saveRequest(null, SystemStatus.ENABLED, false);
    request.setPassword("secret");
    request.setPhone("  ");
    request.setEmail("");
    DepartmentEntity department = new DepartmentEntity();
    department.setId(3L);
    department.setStatus(SystemStatus.ENABLED.getCode());
    when(departmentMapper.selectById(3L)).thenReturn(department);
    when(passwordEncoder.encode("secret")).thenReturn("hash");

    employeeService.create(request);

    ArgumentCaptor<EmployeeEntity> captor = ArgumentCaptor.forClass(EmployeeEntity.class);
    verify(employeeMapper).insert(captor.capture());
    assertThat(captor.getValue().getPasswordHash()).isEqualTo("hash");
    assertThat(captor.getValue().getPhone()).isNull();
    assertThat(captor.getValue().getEmail()).isNull();
    assertThat(captor.getValue().getCreatedAt()).isNotNull();
  }

  /** 部门不存在或禁用时不得保存员工。 */
  @Test
  void shouldRejectDisabledDepartment() {
    EmployeeSaveDTO request = saveRequest(null, SystemStatus.ENABLED, false);
    request.setPassword("secret");
    DepartmentEntity department = new DepartmentEntity();
    department.setStatus(SystemStatus.DISABLED.getCode());
    when(departmentMapper.selectById(3L)).thenReturn(department);

    assertThatThrownBy(() -> employeeService.create(request))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_DEPARTMENT_UNAVAILABLE);
    verify(employeeMapper, never()).insert(any());
  }

  /** 普通员工不能修改自己的超级管理员标记。 */
  @Test
  void shouldRejectSelfSuperuserChangeByNormalEmployee() {
    EmployeeEntity existing = employee(7L, 0, SystemStatus.ENABLED.getCode());
    when(employeeMapper.selectById(7L)).thenReturn(existing);
    EmployeeSaveDTO request = saveRequest(7L, SystemStatus.ENABLED, true);

    assertThatThrownBy(() -> employeeService.update(request, current(7L, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_SELF_OPERATION_FORBIDDEN);
    verify(employeeMapper, never()).updateById(any());
  }

  /** 超级管理员员工不能被禁用。 */
  @Test
  void shouldRejectDisablingSuperuser() {
    when(employeeMapper.selectById(9L)).thenReturn(employee(9L, 1, SystemStatus.ENABLED.getCode()));

    assertThatThrownBy(
            () -> employeeService.changeStatus(9L, SystemStatus.DISABLED, current(1L, true)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_SUPERUSER_PROTECTED);
  }

  /** 保存角色前必须确认全部角色存在且启用，避免部分保存。 */
  @Test
  void shouldValidateAllRolesBeforeReplacingRelations() {
    when(employeeMapper.selectById(9L)).thenReturn(employee(9L, 0, SystemStatus.ENABLED.getCode()));
    when(roleMapper.selectEnabledIds(List.of(2L, 3L))).thenReturn(List.of(2L));

    assertThatThrownBy(() -> employeeService.saveRoles(9L, List.of(2L, 3L, 2L), current(1L, true)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_ROLE_UNAVAILABLE);
    verify(employeeRoleMapper, never()).deleteByEmployeeId(9L);
  }

  private EmployeeSaveDTO saveRequest(Long id, SystemStatus status, boolean superuser) {
    EmployeeSaveDTO request = new EmployeeSaveDTO();
    request.setId(id);
    request.setUsername("zhangsan");
    request.setName("张三");
    request.setDepartmentId(3L);
    request.setStatus(status);
    request.setSuperuser(superuser);
    return request;
  }

  private EmployeeEntity employee(long id, int superuser, int status) {
    EmployeeEntity employee = new EmployeeEntity();
    employee.setId(id);
    employee.setUsername("zhangsan");
    employee.setName("张三");
    employee.setDepartmentId(3L);
    employee.setPasswordHash("old-hash");
    employee.setSuperuser(superuser);
    employee.setStatus(status);
    return employee;
  }

  private CurrentEmployeeVO current(long id, boolean superuser) {
    CurrentEmployeeVO current = new CurrentEmployeeVO();
    current.setId(id);
    current.setSuperuser(superuser);
    return current;
  }
}
