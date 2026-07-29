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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.RoleCreateDTO;
import com.oa.common.model.system.dto.RoleQueryDTO;
import com.oa.common.model.system.dto.RoleUpdateDTO;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.dao.system.EmployeeRoleMapper;
import com.oa.dao.system.RoleMapper;
import com.oa.dao.system.RoleResourceMapper;
import com.oa.dao.system.SystemResourceMapper;
import com.oa.entity.system.RoleEntity;
import com.oa.entity.system.RoleResourceEntity;
import com.oa.entity.system.SystemResourceEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/** 角色管理服务测试。 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {
  @Mock private RoleMapper roleMapper;
  @Mock private EmployeeRoleMapper employeeRoleMapper;
  @Mock private RoleResourceMapper roleResourceMapper;
  @Mock private SystemResourceMapper systemResourceMapper;
  @Mock private PermissionService permissionService;

  private RoleService roleService;

  @BeforeEach
  void setUp() {
    lenient().when(roleMapper.updateBySnapshot(any(), anyInt())).thenReturn(1);
    lenient().when(roleMapper.matchesSnapshot(anyLong(), anyInt())).thenReturn(true);
    roleService =
        new RoleService(
            roleMapper,
            employeeRoleMapper,
            roleResourceMapper,
            systemResourceMapper,
            permissionService);
  }

  /** 分页和详情应转换角色展示模型。 */
  @Test
  void shouldMapPageAndDetail() {
    RoleQueryDTO query = new RoleQueryDTO();
    query.setPage(1);
    query.setSize(10);
    RoleEntity role = role(1L);
    role.setCode("admin");
    role.setName("管理员");
    when(roleMapper.selectRolePage(query))
        .thenReturn(new Page<RoleEntity>(1, 10, 1).setRecords(List.of(role)));
    when(roleMapper.selectById(1L)).thenReturn(role);

    assertThat(roleService.page(query).getRecords()).hasSize(1);
    assertThat(roleService.detail(1L).getCode()).isEqualTo("admin");
  }

  /** 编码或名称已存在时应在写入前拒绝。 */
  @Test
  void shouldPrecheckDuplicateCodeAndName() {
    RoleCreateDTO request = createRequest();
    when(roleMapper.selectByCode("admin")).thenReturn(role(2L));
    assertBusinessCode(() -> roleService.create(request), ExceptionCode.ROLE_CODE_DUPLICATED);
    verify(roleMapper, never()).insert(any(RoleEntity.class));

    org.mockito.Mockito.reset(roleMapper);
    when(roleMapper.selectByName("管理员")).thenReturn(role(3L));
    assertBusinessCode(() -> roleService.create(request), ExceptionCode.ROLE_NAME_DUPLICATED);
    verify(roleMapper, never()).insert(any(RoleEntity.class));
  }

  /** 新增角色应清理文本、写入时间并返回主要字段。 */
  @Test
  void shouldNormalizeAndCreateRole() {
    RoleCreateDTO request = createRequest();
    request.setCode("  admin  ");
    request.setName("  管理员  ");
    request.setDescription("   ");

    var result = roleService.create(request);

    verify(roleMapper).selectByCode("admin");
    verify(roleMapper).selectByName("管理员");
    ArgumentCaptor<RoleEntity> captor = ArgumentCaptor.forClass(RoleEntity.class);
    verify(roleMapper).insert(captor.capture());
    RoleEntity saved = captor.getValue();
    assertThat(saved.getCode()).isEqualTo("admin");
    assertThat(saved.getName()).isEqualTo("管理员");
    assertThat(saved.getDescription()).isNull();
    assertThat(saved.getCreatedAt()).isNotNull().isEqualTo(saved.getUpdatedAt());
    assertThat(result.getCode()).isEqualTo("admin");
    assertThat(result.getName()).isEqualTo("管理员");
    assertThat(result.getStatus()).isEqualTo(SystemStatus.ENABLED);
  }

  /** 修改时同一角色占用编码和名称应放行，状态不变不刷新权限。 */
  @Test
  void shouldAllowSameRoleUniqueValuesWithoutInvalidatingUnchangedStatus() {
    RoleEntity existing = role(1L);
    when(roleMapper.selectById(1L)).thenReturn(existing);
    when(roleMapper.selectByCode("admin")).thenReturn(existing);
    when(roleMapper.selectByName("管理员")).thenReturn(existing);

    roleService.update(updateRequest(SystemStatus.ENABLED));

    verify(roleMapper).updateBySnapshot(existing, SystemStatus.ENABLED.getCode());
    verify(permissionService, never()).invalidateAll();
  }

  /** 修改时其他角色占用编码或名称应拒绝。 */
  @Test
  void shouldRejectUpdateDuplicateCodeAndName() {
    when(roleMapper.selectById(1L)).thenReturn(role(1L));
    when(roleMapper.selectByCode("admin")).thenReturn(role(2L));
    assertBusinessCode(
        () -> roleService.update(updateRequest(SystemStatus.ENABLED)),
        ExceptionCode.ROLE_CODE_DUPLICATED);

    org.mockito.Mockito.reset(roleMapper);
    when(roleMapper.selectById(1L)).thenReturn(role(1L));
    when(roleMapper.selectByName("管理员")).thenReturn(role(2L));
    assertBusinessCode(
        () -> roleService.update(updateRequest(SystemStatus.ENABLED)),
        ExceptionCode.ROLE_NAME_DUPLICATED);
  }

  /** 修改角色状态发生变化时应刷新权限。 */
  @Test
  void shouldInvalidatePermissionWhenUpdateChangesStatus() {
    when(roleMapper.selectById(1L)).thenReturn(role(1L));

    roleService.update(updateRequest(SystemStatus.DISABLED));

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(permissionService, roleMapper);
    order.verify(permissionService).invalidateAll();
    order
        .verify(roleMapper)
        .updateBySnapshot(any(RoleEntity.class), org.mockito.ArgumentMatchers.eq(1));
  }

  /** 完整修改未命中旧状态快照时应报告并发冲突。 */
  @Test
  void shouldRejectConcurrentRoleUpdate() {
    RoleEntity existing = role(1L);
    when(roleMapper.selectById(1L)).thenReturn(existing);
    when(roleMapper.updateBySnapshot(existing, SystemStatus.ENABLED.getCode())).thenReturn(0);

    assertBusinessCode(
        () -> roleService.update(updateRequest(SystemStatus.DISABLED)),
        ExceptionCode.ROLE_CONCURRENT_MODIFICATION);
  }

  /** 状态修改未命中旧状态快照时应报告并发冲突。 */
  @Test
  void shouldRejectConcurrentRoleStatusUpdate() {
    RoleEntity existing = role(1L);
    when(roleMapper.selectById(1L)).thenReturn(existing);
    when(roleMapper.updateBySnapshot(existing, SystemStatus.ENABLED.getCode())).thenReturn(0);

    assertBusinessCode(
        () -> roleService.changeStatus(1L, SystemStatus.DISABLED),
        ExceptionCode.ROLE_CONCURRENT_MODIFICATION);
  }

  /** 状态未变化时也应重新确认角色快照，避免并发状态变化被静默忽略。 */
  @Test
  void shouldRejectConcurrentRoleChangeOnStatusNoOp() {
    RoleEntity initial = role(1L);
    when(roleMapper.selectById(1L)).thenReturn(initial);
    when(roleMapper.matchesSnapshot(1L, SystemStatus.ENABLED.getCode())).thenReturn(false);

    assertBusinessCode(
        () -> roleService.changeStatus(1L, SystemStatus.ENABLED),
        ExceptionCode.ROLE_CONCURRENT_MODIFICATION);
  }

  /** 修改唯一索引冲突应精确转换，未知冲突应透传。 */
  @Test
  void shouldTranslateUpdateDuplicateKeysAndKeepUnknownCause() {
    when(roleMapper.selectById(1L)).thenReturn(role(1L));
    when(roleMapper.updateBySnapshot(any(RoleEntity.class), anyInt()))
        .thenThrow(new DuplicateKeyException("udx_role_code"))
        .thenThrow(new DuplicateKeyException("udx_role_name"))
        .thenThrow(new DuplicateKeyException("other_index"));
    assertBusinessCode(
        () -> roleService.update(updateRequest(SystemStatus.ENABLED)),
        ExceptionCode.ROLE_CODE_DUPLICATED);
    assertBusinessCode(
        () -> roleService.update(updateRequest(SystemStatus.ENABLED)),
        ExceptionCode.ROLE_NAME_DUPLICATED);
    assertThatThrownBy(() -> roleService.update(updateRequest(SystemStatus.ENABLED)))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("other_index");
  }

  /** 唯一索引并发冲突应精确转换，未知冲突应透传。 */
  @Test
  void shouldTranslateKnownDuplicateKeysAndKeepUnknownCause() {
    RoleCreateDTO request = createRequest();
    when(roleMapper.insert(any(RoleEntity.class)))
        .thenThrow(new DuplicateKeyException("udx_role_code"))
        .thenThrow(new DuplicateKeyException("udx_role_name"))
        .thenThrow(new DuplicateKeyException("other_index"));
    assertBusinessCode(() -> roleService.create(request), ExceptionCode.ROLE_CODE_DUPLICATED);
    assertBusinessCode(() -> roleService.create(request), ExceptionCode.ROLE_NAME_DUPLICATED);
    assertThatThrownBy(() -> roleService.create(request))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("other_index");
  }

  /** 状态实际变化时刷新权限，无变化时不写数据库。 */
  @Test
  void shouldOnlyUpdateChangedStatus() {
    when(roleMapper.selectById(1L)).thenReturn(role(1L));
    roleService.changeStatus(1L, SystemStatus.ENABLED);
    verify(roleMapper, never()).updateBySnapshot(any(RoleEntity.class), anyInt());
    verify(permissionService, never()).invalidateAll();

    roleService.changeStatus(1L, SystemStatus.DISABLED);
    org.mockito.InOrder order = org.mockito.Mockito.inOrder(permissionService, roleMapper);
    order.verify(permissionService).invalidateAll();
    order
        .verify(roleMapper)
        .updateBySnapshot(any(RoleEntity.class), org.mockito.ArgumentMatchers.eq(1));
  }

  /** 无员工关联时应先清理资源关联再删除角色并刷新权限。 */
  @Test
  void shouldDeleteRoleAndRelations() {
    when(roleMapper.selectByIdForUpdate(1L)).thenReturn(role(1L));
    roleService.delete(1L);
    org.mockito.InOrder order =
        org.mockito.Mockito.inOrder(
            permissionService, roleMapper, employeeRoleMapper, roleResourceMapper);
    order.verify(permissionService).invalidateAll();
    order.verify(roleMapper).selectByIdForUpdate(1L);
    order.verify(employeeRoleMapper).countByRoleId(1L);
    order.verify(roleResourceMapper).deleteByRoleId(1L);
    order.verify(roleMapper).deleteById(1L);
    verify(permissionService).invalidateAll();
  }

  /** 角色不存在时不得保存资源。 */
  @Test
  void shouldRejectResourcesForMissingRole() {
    assertBusinessCode(
        () -> roleService.saveResources(1L, List.of(2L)), ExceptionCode.ROLE_NOT_FOUND);
    verify(permissionService).invalidateAll();
    verify(roleMapper).selectByIdForUpdate(1L);
    verify(systemResourceMapper, never()).selectByIdsForUpdate(any());
    verify(roleResourceMapper, never()).deleteByRoleId(1L);
  }

  /** 角色关联员工时应拒绝删除。 */
  @Test
  void shouldRejectDeletingRoleWithEmployees() {
    when(roleMapper.selectByIdForUpdate(1L)).thenReturn(role(1L));
    when(employeeRoleMapper.countByRoleId(1L)).thenReturn(1L);

    assertThatThrownBy(() -> roleService.delete(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.ROLE_HAS_EMPLOYEES);
    verify(roleMapper, never()).deleteById(1L);
  }

  /** 保存资源应去重后全量替换并刷新权限版本。 */
  @Test
  void shouldDeduplicateAndSaveResources() {
    when(roleMapper.selectByIdForUpdate(1L)).thenReturn(role(1L));
    when(systemResourceMapper.selectByIdsForUpdate(List.of(2L, 3L)))
        .thenReturn(
            List.of(resource(2L, SystemStatus.ENABLED), resource(3L, SystemStatus.ENABLED)));

    roleService.saveResources(1L, List.of(2L, 2L, 3L));

    verify(roleResourceMapper).deleteByRoleId(1L);
    ArgumentCaptor<List<RoleResourceEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(roleResourceMapper).insertBatch(captor.capture());
    assertThat(captor.getValue())
        .extracting(RoleResourceEntity::getResourceId)
        .containsExactly(2L, 3L);
    org.mockito.InOrder order =
        org.mockito.Mockito.inOrder(
            permissionService, roleMapper, systemResourceMapper, roleResourceMapper);
    order.verify(permissionService).invalidateAll();
    order.verify(roleMapper).selectByIdForUpdate(1L);
    order.verify(systemResourceMapper).selectByIdsForUpdate(List.of(2L, 3L));
    order.verify(roleResourceMapper).deleteByRoleId(1L);
    order.verify(roleResourceMapper).insertBatch(any());
  }

  /** 资源不存在或禁用时不得修改原授权。 */
  @Test
  void shouldRejectUnavailableResourcesBeforeDeletingRelations() {
    when(roleMapper.selectByIdForUpdate(1L)).thenReturn(role(1L));
    when(systemResourceMapper.selectByIdsForUpdate(List.of(2L, 3L)))
        .thenReturn(List.of(resource(2L, SystemStatus.ENABLED)));

    assertThatThrownBy(() -> roleService.saveResources(1L, List.of(2L, 3L)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(ExceptionCode.ROLE_RESOURCE_UNAVAILABLE);
    verify(roleResourceMapper, never()).deleteByRoleId(1L);
  }

  /** 空资源列表应清空授权并刷新权限版本。 */
  @Test
  void shouldClearResourcesWithEmptyList() {
    when(roleMapper.selectByIdForUpdate(1L)).thenReturn(role(1L));

    roleService.saveResources(1L, List.of());

    verify(roleResourceMapper).deleteByRoleId(1L);
    verify(roleResourceMapper, never()).insertBatch(any());
    verify(permissionService).invalidateAll();
  }

  /** 角色资源超过单批上限时应分批写入。 */
  @Test
  void shouldInsertResourcesInBatchesOfFiveHundred() {
    when(roleMapper.selectByIdForUpdate(1L)).thenReturn(role(1L));
    List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 501).boxed().toList();
    when(systemResourceMapper.selectByIdsForUpdate(ids))
        .thenReturn(ids.stream().map(id -> resource(id, SystemStatus.ENABLED)).toList());

    roleService.saveResources(1L, ids);

    ArgumentCaptor<List<RoleResourceEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(roleResourceMapper, org.mockito.Mockito.times(2)).insertBatch(captor.capture());
    assertThat(captor.getAllValues()).extracting(List::size).containsExactly(500, 1);
  }

  /** 授权回显应先确认角色存在再查询单表关联。 */
  @Test
  void shouldReturnRoleResourceIds() {
    when(roleMapper.selectById(1L)).thenReturn(role(1L));
    when(roleResourceMapper.selectResourceIdsByRoleId(1L)).thenReturn(List.of(2L, 3L));

    assertThat(roleService.resourceIds(1L)).containsExactly(2L, 3L);
  }

  private RoleEntity role(long id) {
    RoleEntity role = new RoleEntity();
    role.setId(id);
    role.setStatus(SystemStatus.ENABLED.getCode());
    return role;
  }

  private SystemResourceEntity resource(long id, SystemStatus status) {
    SystemResourceEntity resource = new SystemResourceEntity();
    resource.setId(id);
    resource.setStatus(status.getCode());
    return resource;
  }

  private RoleCreateDTO createRequest() {
    RoleCreateDTO request = new RoleCreateDTO();
    request.setCode("admin");
    request.setName("管理员");
    request.setStatus(SystemStatus.ENABLED);
    return request;
  }

  private RoleUpdateDTO updateRequest(SystemStatus status) {
    RoleUpdateDTO request = new RoleUpdateDTO();
    request.setId(1L);
    request.setCode("admin");
    request.setName("管理员");
    request.setStatus(status);
    return request;
  }

  private void assertBusinessCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ExceptionCode code) {
    assertThatThrownBy(action)
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(code);
  }
}
