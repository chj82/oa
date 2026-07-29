package com.oa.service.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.EmployeeCreateDTO;
import com.oa.common.model.system.dto.EmployeeQueryDTO;
import com.oa.common.model.system.dto.EmployeeUpdateDTO;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.model.system.vo.CurrentEmployeeVO;
import com.oa.common.response.PageResult;
import com.oa.dao.system.DepartmentMapper;
import com.oa.dao.system.EmployeeMapper;
import com.oa.dao.system.EmployeeRoleMapper;
import com.oa.dao.system.RoleMapper;
import com.oa.entity.system.DepartmentEntity;
import com.oa.entity.system.EmployeeEntity;
import com.oa.entity.system.EmployeeRoleEntity;
import com.oa.entity.system.RoleEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    lenient().when(employeeMapper.updateBySnapshot(any(), anyInt(), anyInt())).thenReturn(1);
    lenient().when(employeeMapper.matchesSnapshot(anyLong(), anyInt(), anyInt())).thenReturn(true);
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

  /** 角色回显应先确认员工存在，再按Mapper稳定顺序返回角色ID。 */
  @Test
  void shouldReturnOrderedRoleIdsForExistingEmployee() {
    when(employeeMapper.selectById(9L)).thenReturn(employee(9L, 0, SystemStatus.ENABLED.getCode()));
    when(employeeRoleMapper.selectRoleIdsByEmployeeId(9L)).thenReturn(List.of(2L, 3L));

    assertThat(employeeService.roleIds(9L)).containsExactly(2L, 3L);

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(employeeMapper, employeeRoleMapper);
    order.verify(employeeMapper).selectById(9L);
    order.verify(employeeRoleMapper).selectRoleIdsByEmployeeId(9L);
    verify(employeeMapper, never()).selectByIdForUpdate(9L);
  }

  /** 员工不存在时角色回显不得查询关联。 */
  @Test
  void shouldRejectRoleIdsForMissingEmployee() {
    assertThatThrownBy(() -> employeeService.roleIds(9L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_NOT_FOUND);
    verify(employeeRoleMapper, never()).selectRoleIdsByEmployeeId(9L);
  }

  /** 新增员工应验证部门、编码密码并把空联系方式写为NULL。 */
  @Test
  void shouldCreateEmployeeWithEncodedPasswordAndNullContacts() {
    EmployeeCreateDTO request = createRequest(SystemStatus.ENABLED, false);
    request.setPassword("secret");
    request.setPhone("  ");
    request.setEmail("");
    DepartmentEntity department = new DepartmentEntity();
    department.setId(3L);
    department.setStatus(SystemStatus.ENABLED.getCode());
    when(departmentMapper.selectByIdForUpdate(3L)).thenReturn(department);
    when(passwordEncoder.encode("secret")).thenReturn("hash");

    employeeService.create(request);

    ArgumentCaptor<EmployeeEntity> captor = ArgumentCaptor.forClass(EmployeeEntity.class);
    verify(employeeMapper).insert(captor.capture());
    assertThat(captor.getValue().getPasswordHash()).isEqualTo("hash");
    assertThat(captor.getValue().getPhone()).isNull();
    assertThat(captor.getValue().getEmail()).isNull();
    assertThat(captor.getValue().getCreatedAt()).isNotNull();
    org.mockito.InOrder order = org.mockito.Mockito.inOrder(departmentMapper, employeeMapper);
    order.verify(departmentMapper).selectByIdForUpdate(3L);
    order.verify(employeeMapper).insert(any(EmployeeEntity.class));
  }

  /** 部门不存在或禁用时不得保存员工。 */
  @Test
  void shouldRejectDisabledDepartment() {
    EmployeeCreateDTO request = createRequest(SystemStatus.ENABLED, false);
    request.setPassword("secret");
    DepartmentEntity department = new DepartmentEntity();
    department.setStatus(SystemStatus.DISABLED.getCode());
    when(departmentMapper.selectByIdForUpdate(3L)).thenReturn(department);

    assertThatThrownBy(() -> employeeService.create(request))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_DEPARTMENT_UNAVAILABLE);
    verify(employeeMapper, never()).insert(any(EmployeeEntity.class));
  }

  /** 普通员工不能修改自己的超级管理员标记。 */
  @Test
  void shouldRejectSelfSuperuserChangeByNormalEmployee() {
    EmployeeEntity existing = employee(7L, 0, SystemStatus.ENABLED.getCode());
    when(employeeMapper.selectById(7L)).thenReturn(existing);
    EmployeeUpdateDTO request = updateRequest(7L, SystemStatus.ENABLED, true);

    assertThatThrownBy(() -> employeeService.update(request, current(7L, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_SELF_OPERATION_FORBIDDEN);
    verify(employeeMapper, never()).updateById(any(EmployeeEntity.class));
  }

  /** 普通员工不能通过基本信息修改入口修改自己的状态。 */
  @Test
  void shouldRejectSelfStatusChangeThroughUpdate() {
    EmployeeEntity existing = employee(7L, 0, SystemStatus.ENABLED.getCode());
    when(employeeMapper.selectById(7L)).thenReturn(existing);
    EmployeeUpdateDTO request = updateRequest(7L, SystemStatus.DISABLED, false);

    assertThatThrownBy(() -> employeeService.update(request, current(7L, false)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_SELF_OPERATION_FORBIDDEN);
  }

  /** 超级管理员不能通过基本信息修改入口被禁用。 */
  @Test
  void shouldRejectDisablingSuperuserThroughUpdate() {
    EmployeeEntity existing = employee(9L, 1, SystemStatus.ENABLED.getCode());
    when(employeeMapper.selectById(9L)).thenReturn(existing);
    EmployeeUpdateDTO request = updateRequest(9L, SystemStatus.DISABLED, true);

    assertThatThrownBy(() -> employeeService.update(request, current(1L, true)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_SUPERUSER_PROTECTED);
  }

  /** 已是超级管理员的员工不能降级为普通员工。 */
  @Test
  void shouldRejectDemotingExistingSuperuser() {
    EmployeeEntity existing = employee(9L, 1, SystemStatus.ENABLED.getCode());
    when(employeeMapper.selectById(9L)).thenReturn(existing);
    EmployeeUpdateDTO request = updateRequest(9L, SystemStatus.ENABLED, false);

    assertThatThrownBy(() -> employeeService.update(request, current(1L, true)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_SUPERUSER_DEMOTION_FORBIDDEN);
  }

  /** 修改员工基本信息不得修改密码。 */
  @Test
  void shouldNeverChangePasswordThroughUpdate() {
    EmployeeEntity existing = employee(9L, 0, SystemStatus.ENABLED.getCode());
    when(employeeMapper.selectById(9L)).thenReturn(existing);
    DepartmentEntity department = new DepartmentEntity();
    department.setStatus(SystemStatus.ENABLED.getCode());
    when(departmentMapper.selectByIdForUpdate(3L)).thenReturn(department);
    EmployeeUpdateDTO request = updateRequest(9L, SystemStatus.ENABLED, false);

    employeeService.update(request, current(1L, true));

    assertThat(existing.getPasswordHash()).isEqualTo("old-hash");
    verify(passwordEncoder, never()).encode(any());
    verify(sessionService, never()).invalidateEmployeeSessions(9L);
    verify(permissionService, never()).invalidateAll();
    org.mockito.InOrder order = org.mockito.Mockito.inOrder(employeeMapper, departmentMapper);
    order.verify(employeeMapper).selectById(9L);
    order.verify(departmentMapper).selectByIdForUpdate(3L);
    order.verify(employeeMapper).updateBySnapshot(existing, SystemStatus.ENABLED.getCode(), 0);
  }

  /** 权限语义变化时必须在获取部门行锁前标记权限失效。 */
  @Test
  void shouldInvalidatePermissionsBeforeDepartmentLockOnSemanticUpdate() {
    EmployeeEntity existing = employee(9L, 0, SystemStatus.ENABLED.getCode());
    when(employeeMapper.selectById(9L)).thenReturn(existing);
    DepartmentEntity department = new DepartmentEntity();
    department.setStatus(SystemStatus.ENABLED.getCode());
    when(departmentMapper.selectByIdForUpdate(3L)).thenReturn(department);
    EmployeeUpdateDTO request = updateRequest(9L, SystemStatus.DISABLED, false);

    employeeService.update(request, current(1L, true));

    org.mockito.InOrder order =
        org.mockito.Mockito.inOrder(employeeMapper, permissionService, departmentMapper);
    order.verify(employeeMapper).selectById(9L);
    order.verify(permissionService).invalidateAll();
    order.verify(departmentMapper).selectByIdForUpdate(3L);
    order.verify(employeeMapper).updateBySnapshot(existing, SystemStatus.ENABLED.getCode(), 0);
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

  /** 员工状态实际变化时必须先标记权限失效，再更新数据库。 */
  @Test
  void shouldInvalidatePermissionsBeforeUpdatingEmployeeStatus() {
    EmployeeEntity existing = employee(9L, 0, SystemStatus.ENABLED.getCode());
    when(employeeMapper.selectById(9L)).thenReturn(existing);

    employeeService.changeStatus(9L, SystemStatus.DISABLED, current(1L, true));

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(permissionService, employeeMapper);
    order.verify(permissionService).invalidateAll();
    order.verify(employeeMapper).updateBySnapshot(existing, SystemStatus.ENABLED.getCode(), 0);
  }

  /** 完整修改未命中旧状态和超级管理员快照时应报告并发冲突。 */
  @Test
  void shouldRejectConcurrentEmployeeUpdate() {
    EmployeeEntity existing = employee(9L, 0, SystemStatus.ENABLED.getCode());
    when(employeeMapper.selectById(9L)).thenReturn(existing);
    DepartmentEntity department = new DepartmentEntity();
    department.setStatus(SystemStatus.ENABLED.getCode());
    when(departmentMapper.selectByIdForUpdate(3L)).thenReturn(department);
    when(employeeMapper.updateBySnapshot(existing, SystemStatus.ENABLED.getCode(), 0))
        .thenReturn(0);

    assertThatThrownBy(
            () ->
                employeeService.update(
                    updateRequest(9L, SystemStatus.DISABLED, false), current(1L, true)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_CONCURRENT_MODIFICATION);
    verify(sessionService, never()).invalidateEmployeeSessions(9L);
  }

  /** 状态修改未命中旧状态和超级管理员快照时应报告并发冲突。 */
  @Test
  void shouldRejectConcurrentEmployeeStatusUpdate() {
    EmployeeEntity existing = employee(9L, 0, SystemStatus.ENABLED.getCode());
    when(employeeMapper.selectById(9L)).thenReturn(existing);
    when(employeeMapper.updateBySnapshot(existing, SystemStatus.ENABLED.getCode(), 0))
        .thenReturn(0);

    assertThatThrownBy(
            () -> employeeService.changeStatus(9L, SystemStatus.DISABLED, current(1L, true)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_CONCURRENT_MODIFICATION);
    verify(sessionService, never()).invalidateEmployeeSessions(9L);
  }

  /** 状态未变化时也应重新确认员工快照，避免并发超级管理员变化被静默忽略。 */
  @Test
  void shouldRejectConcurrentEmployeeChangeOnStatusNoOp() {
    EmployeeEntity initial = employee(9L, 0, SystemStatus.ENABLED.getCode());
    when(employeeMapper.selectById(9L)).thenReturn(initial);
    when(employeeMapper.matchesSnapshot(9L, SystemStatus.ENABLED.getCode(), 0)).thenReturn(false);

    assertThatThrownBy(
            () -> employeeService.changeStatus(9L, SystemStatus.ENABLED, current(1L, true)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_CONCURRENT_MODIFICATION);
  }

  /** 保存角色前必须确认全部角色存在且启用，避免部分保存。 */
  @Test
  void shouldValidateAllRolesBeforeReplacingRelations() {
    when(employeeMapper.selectByIdForUpdate(9L))
        .thenReturn(employee(9L, 0, SystemStatus.ENABLED.getCode()));
    when(roleMapper.selectByIdsForUpdate(List.of(2L, 3L))).thenReturn(List.of(role(2L)));

    assertThatThrownBy(() -> employeeService.saveRoles(9L, List.of(2L, 3L, 2L), current(1L, true)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_ROLE_UNAVAILABLE);
    verify(employeeRoleMapper, never()).deleteByEmployeeId(9L);
  }

  /** 角色保存应去重后全量替换并失效全部权限缓存。 */
  @Test
  void shouldReplaceDistinctRolesAndInvalidatePermissions() {
    when(employeeMapper.selectByIdForUpdate(9L))
        .thenReturn(employee(9L, 0, SystemStatus.ENABLED.getCode()));
    when(roleMapper.selectByIdsForUpdate(List.of(2L, 3L))).thenReturn(List.of(role(2L), role(3L)));

    employeeService.saveRoles(9L, List.of(3L, 2L, 3L), current(1L, true));

    verify(employeeRoleMapper).deleteByEmployeeId(9L);
    ArgumentCaptor<List<EmployeeRoleEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(employeeRoleMapper).insertBatch(captor.capture());
    assertThat(captor.getValue()).extracting(EmployeeRoleEntity::getRoleId).containsExactly(2L, 3L);
    org.mockito.InOrder order =
        org.mockito.Mockito.inOrder(
            permissionService, employeeMapper, roleMapper, employeeRoleMapper);
    order.verify(permissionService).invalidateAll();
    order.verify(employeeMapper).selectByIdForUpdate(9L);
    order.verify(roleMapper).selectByIdsForUpdate(List.of(2L, 3L));
    order.verify(employeeRoleMapper).deleteByEmployeeId(9L);
    order.verify(employeeRoleMapper).insertBatch(any());
  }

  /** 员工角色超过单批上限时应按角色ID排序锁定并分批写入。 */
  @Test
  void shouldLockSortedRolesAndInsertInBatchesOfFiveHundred() {
    when(employeeMapper.selectByIdForUpdate(9L))
        .thenReturn(employee(9L, 0, SystemStatus.ENABLED.getCode()));
    List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 501).boxed().toList();
    List<RoleEntity> roles = ids.stream().map(this::role).toList();
    when(roleMapper.selectByIdsForUpdate(ids)).thenReturn(roles);
    List<Long> reversedIds = new java.util.ArrayList<>(ids);
    java.util.Collections.reverse(reversedIds);

    employeeService.saveRoles(9L, reversedIds, current(1L, true));

    verify(roleMapper).selectByIdsForUpdate(ids);
    ArgumentCaptor<List<EmployeeRoleEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(employeeRoleMapper, org.mockito.Mockito.times(2)).insertBatch(captor.capture());
    assertThat(captor.getAllValues()).extracting(List::size).containsExactly(500, 1);
  }

  /** 清空员工角色时不应执行角色锁定和批量插入。 */
  @Test
  void shouldClearRolesWithoutLockOrInsert() {
    when(employeeMapper.selectByIdForUpdate(9L))
        .thenReturn(employee(9L, 0, SystemStatus.ENABLED.getCode()));

    employeeService.saveRoles(9L, List.of(), current(1L, true));

    verify(roleMapper, never()).selectByIdsForUpdate(any());
    verify(employeeRoleMapper).deleteByEmployeeId(9L);
    verify(employeeRoleMapper, never()).insertBatch(any());
  }

  /** 删除员工应在同一事务处理角色关联，并在提交后失效会话。 */
  @Test
  void shouldDeleteRelationsAndInvalidateSessionsAfterCommit() {
    when(employeeMapper.selectByIdForUpdate(9L))
        .thenReturn(employee(9L, 0, SystemStatus.ENABLED.getCode()));
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      employeeService.delete(9L, 1L);

      verify(employeeRoleMapper).deleteByEmployeeId(9L);
      verify(employeeMapper).deleteById(9L);
      org.mockito.InOrder order =
          org.mockito.Mockito.inOrder(permissionService, employeeMapper, employeeRoleMapper);
      order.verify(permissionService).invalidateAll();
      order.verify(employeeMapper).selectByIdForUpdate(9L);
      order.verify(employeeRoleMapper).deleteByEmployeeId(9L);
      order.verify(employeeMapper).deleteById(9L);
      verify(sessionService, never()).invalidateEmployeeSessions(9L);
      commitSynchronizations();
      verify(sessionService).invalidateEmployeeSessions(9L);
    } finally {
      clearTransactionSynchronization();
    }
  }

  /** 员工不能删除自己。 */
  @Test
  void shouldRejectDeletingSelf() {
    assertThatThrownBy(() -> employeeService.delete(9L, 9L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_SELF_DELETE_FORBIDDEN);
    verify(employeeMapper, never()).deleteById(9L);
    verify(permissionService, never()).invalidateAll();
  }

  /** 删除锁定后员工不存在时应停止，不得删除关联或员工。 */
  @Test
  void shouldRejectDeleteWhenLockedEmployeeMissing() {
    assertThatThrownBy(() -> employeeService.delete(9L, 1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_NOT_FOUND);

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(permissionService, employeeMapper);
    order.verify(permissionService).invalidateAll();
    order.verify(employeeMapper).selectByIdForUpdate(9L);
    verify(employeeRoleMapper, never()).deleteByEmployeeId(9L);
    verify(employeeMapper, never()).deleteById(9L);
  }

  /** 保存角色锁定后员工不存在时不得锁角色或修改关联。 */
  @Test
  void shouldRejectSaveRolesWhenLockedEmployeeMissing() {
    assertThatThrownBy(() -> employeeService.saveRoles(9L, List.of(2L), current(1L, true)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_NOT_FOUND);

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(permissionService, employeeMapper);
    order.verify(permissionService).invalidateAll();
    order.verify(employeeMapper).selectByIdForUpdate(9L);
    verify(roleMapper, never()).selectByIdsForUpdate(any());
    verify(employeeRoleMapper, never()).deleteByEmployeeId(9L);
  }

  /** 创建并发命中用户名唯一索引时返回用户名重复业务异常。 */
  @Test
  void shouldTranslateUsernameDuplicateKeyOnCreate() {
    assertCreateDuplicateKey(
        "Duplicate entry 'zhangsan' for key 'udx_employee_username'",
        ExceptionCode.EMPLOYEE_USERNAME_DUPLICATED);
  }

  /** 创建并发命中手机号唯一索引时返回手机号重复业务异常。 */
  @Test
  void shouldTranslatePhoneDuplicateKeyOnCreate() {
    assertCreateDuplicateKey(
        "Duplicate entry '13800000000' for key 'udx_employee_phone'",
        ExceptionCode.EMPLOYEE_PHONE_DUPLICATED);
  }

  /** 创建并发命中邮箱唯一索引时返回邮箱重复业务异常。 */
  @Test
  void shouldTranslateEmailDuplicateKeyOnCreate() {
    assertCreateDuplicateKey(
        "Duplicate entry 'a@example.com' for key 'udx_employee_email'",
        ExceptionCode.EMPLOYEE_EMAIL_DUPLICATED);
  }

  /** 未识别的唯一索引异常不得被错误转换。 */
  @Test
  void shouldRethrowUnknownDuplicateKey() {
    DuplicateKeyException duplicate =
        new DuplicateKeyException("Duplicate entry for key 'udx_unknown'");
    prepareCreate();
    org.mockito.Mockito.doThrow(duplicate).when(employeeMapper).insert(any(EmployeeEntity.class));

    EmployeeCreateDTO request = createRequest(SystemStatus.ENABLED, false);
    request.setPassword("secret");
    assertThatThrownBy(() -> employeeService.create(request)).isSameAs(duplicate);
  }

  /** 修改并发命中邮箱唯一索引时也应转换业务异常。 */
  @Test
  void shouldTranslateDuplicateKeyOnUpdate() {
    EmployeeEntity existing = employee(9L, 0, SystemStatus.ENABLED.getCode());
    when(employeeMapper.selectById(9L)).thenReturn(existing);
    DepartmentEntity department = new DepartmentEntity();
    department.setStatus(SystemStatus.ENABLED.getCode());
    when(departmentMapper.selectByIdForUpdate(3L)).thenReturn(department);
    org.mockito.Mockito.doThrow(
            new DuplicateKeyException("Duplicate entry for key 'udx_employee_email'"))
        .when(employeeMapper)
        .updateBySnapshot(existing, SystemStatus.ENABLED.getCode(), 0);

    assertThatThrownBy(
            () ->
                employeeService.update(
                    updateRequest(9L, SystemStatus.ENABLED, false), current(1L, true)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_EMAIL_DUPLICATED);
  }

  /** BCrypt输入超过72个UTF-8字节时不得创建员工。 */
  @Test
  void shouldRejectCreatePasswordLongerThanBcryptByteLimit() {
    EmployeeCreateDTO request = createRequest(SystemStatus.ENABLED, false);
    request.setPassword("密".repeat(25));

    assertThatThrownBy(() -> employeeService.create(request))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_PASSWORD_TOO_LONG);
    verify(employeeMapper, never()).insert(any(EmployeeEntity.class));
  }

  /** BCrypt输入超过72个UTF-8字节时不得重置密码。 */
  @Test
  void shouldRejectResetPasswordLongerThanBcryptByteLimit() {
    assertThatThrownBy(() -> employeeService.resetPassword(9L, "密".repeat(25)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.EMPLOYEE_PASSWORD_TOO_LONG);
    verify(employeeMapper, never()).updateById(any(EmployeeEntity.class));
  }

  private EmployeeCreateDTO createRequest(SystemStatus status, boolean superuser) {
    EmployeeCreateDTO request = new EmployeeCreateDTO();
    request.setUsername("zhangsan");
    request.setName("张三");
    request.setDepartmentId(3L);
    request.setStatus(status);
    request.setSuperuser(superuser);
    return request;
  }

  private void assertCreateDuplicateKey(String message, ExceptionCode expectedCode) {
    prepareCreate();
    org.mockito.Mockito.doThrow(new DuplicateKeyException(message))
        .when(employeeMapper)
        .insert(any(EmployeeEntity.class));

    EmployeeCreateDTO request = createRequest(SystemStatus.ENABLED, false);
    request.setPassword("secret");
    assertThatThrownBy(() -> employeeService.create(request))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(expectedCode);
  }

  private void prepareCreate() {
    DepartmentEntity department = new DepartmentEntity();
    department.setStatus(SystemStatus.ENABLED.getCode());
    when(departmentMapper.selectByIdForUpdate(3L)).thenReturn(department);
    when(passwordEncoder.encode("secret")).thenReturn("hash");
  }

  private EmployeeUpdateDTO updateRequest(long id, SystemStatus status, boolean superuser) {
    EmployeeUpdateDTO request = new EmployeeUpdateDTO();
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

  private RoleEntity role(long id) {
    RoleEntity role = new RoleEntity();
    role.setId(id);
    role.setStatus(SystemStatus.ENABLED.getCode());
    return role;
  }

  private CurrentEmployeeVO current(long id, boolean superuser) {
    CurrentEmployeeVO current = new CurrentEmployeeVO();
    current.setId(id);
    current.setSuperuser(superuser);
    return current;
  }

  private void commitSynchronizations() {
    for (TransactionSynchronization synchronization :
        TransactionSynchronizationManager.getSynchronizations()) {
      synchronization.afterCommit();
    }
  }

  private void clearTransactionSynchronization() {
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }
}
