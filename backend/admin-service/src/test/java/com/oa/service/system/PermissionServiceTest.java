package com.oa.service.system;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.system.cache.EmployeePermissionCache;
import com.oa.dao.system.EmployeeMapper;
import com.oa.dao.system.EmployeeRoleMapper;
import com.oa.dao.system.ResourceApiMapper;
import com.oa.dao.system.RoleMapper;
import com.oa.dao.system.RoleResourceMapper;
import com.oa.dao.system.SystemApiMapper;
import com.oa.dao.system.SystemResourceMapper;
import com.oa.entity.system.EmployeeEntity;
import com.oa.entity.system.SystemResourceEntity;
import com.oa.service.system.store.StringRedisPermissionStore;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Redis 缓存和单表分步资源权限服务测试。 */
class PermissionServiceTest {
  @Mock private EmployeeMapper employeeMapper;
  @Mock private EmployeeRoleMapper employeeRoleMapper;
  @Mock private RoleMapper roleMapper;
  @Mock private RoleResourceMapper roleResourceMapper;
  @Mock private SystemResourceMapper systemResourceMapper;
  @Mock private ResourceApiMapper resourceApiMapper;
  @Mock private SystemApiMapper systemApiMapper;
  @Mock private StringRedisPermissionStore permissionStore;
  private PermissionService permissionService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    permissionService =
        new PermissionService(
            employeeMapper,
            employeeRoleMapper,
            roleMapper,
            roleResourceMapper,
            systemResourceMapper,
            resourceApiMapper,
            systemApiMapper,
            permissionStore);
    when(permissionStore.tryAcquireRebuildLock(anyLong())).thenReturn("lock-token");
    when(permissionStore.putIfCurrent(anyLong(), any(EmployeePermissionCache.class)))
        .thenReturn(true);
  }

  /** 缓存命中时直接判断路由模板，不访问任何 Mapper。 */
  @Test
  void 缓存命中不访问数据库() {
    when(permissionStore.getIfCurrent(7L)).thenReturn(cache(3L, "/api/orders/{id}"));

    assertTrue(permissionService.hasPermission(7L, "/api/orders/{id}"));

    verify(permissionStore, never()).currentVersion();
    verifyNoInteractions(
        employeeMapper,
        employeeRoleMapper,
        roleMapper,
        roleResourceMapper,
        systemResourceMapper,
        resourceApiMapper,
        systemApiMapper);
  }

  /** 高频缓存读取方法不得开启数据库事务，避免命中时申请数据库连接。 */
  @Test
  void 缓存鉴权方法不创建数据库事务() throws Exception {
    assertFalse(
        PermissionService.class
            .getMethod("hasPermission", long.class, String.class)
            .isAnnotationPresent(Transactional.class));
  }

  /** 缓存缺失或版本不一致时按单表权限链重建全部有效接口路径并写回缓存。 */
  @Test
  void 缓存失效后重建普通员工权限() {
    when(permissionStore.getIfCurrent(7L)).thenReturn(null);
    when(permissionStore.currentVersion()).thenReturn(4L);
    when(employeeMapper.selectById(7L)).thenReturn(employee(1));
    when(employeeRoleMapper.selectRoleIdsByEmployeeId(7L)).thenReturn(List.of(1L, 2L, 3L));
    when(roleMapper.selectEnabledIds(List.of(1L, 2L, 3L))).thenReturn(List.of(1L, 3L));
    when(roleResourceMapper.selectResourceIdsByRoleIds(List.of(1L, 3L)))
        .thenReturn(List.of(10L, 11L));
    when(systemResourceMapper.selectExistingByIds(Set.of(10L, 11L)))
        .thenReturn(List.of(resource(10L, 100L, 1), resource(11L, 0L, 1)));
    when(systemResourceMapper.selectExistingByIds(Set.of(100L)))
        .thenReturn(List.of(resource(100L, 0L, 1)));
    when(resourceApiMapper.selectApiIdsByResourceIds(Set.of(10L, 11L)))
        .thenReturn(List.of(20L, 21L));
    when(systemApiMapper.selectEnabledPathsByIds(List.of(20L, 21L)))
        .thenReturn(List.of("/api/orders/{id}"));

    assertTrue(permissionService.hasPermission(7L, "/api/orders/{id}"));

    ArgumentCaptor<EmployeePermissionCache> cacheCaptor =
        ArgumentCaptor.forClass(EmployeePermissionCache.class);
    verify(permissionStore)
        .putIfCurrent(org.mockito.ArgumentMatchers.eq(7L), cacheCaptor.capture());
    assertTrue(cacheCaptor.getValue().getVersion() == 4L);
    assertTrue(cacheCaptor.getValue().getApiPaths().contains("/api/orders/{id}"));
    verify(permissionStore).releaseRebuildLock(7L, "lock-token");
  }

  /** 获取重建锁后必须二次读取缓存，已由前一持有者写回时不得查询数据库。 */
  @Test
  void 获取锁后二次命中不访问数据库() {
    when(permissionStore.getIfCurrent(7L)).thenReturn(null, cache(3L, "/api/orders/{id}"));

    assertTrue(permissionService.hasPermission(7L, "/api/orders/{id}"));

    verifyNoInteractions(
        employeeMapper,
        employeeRoleMapper,
        roleMapper,
        roleResourceMapper,
        systemResourceMapper,
        resourceApiMapper,
        systemApiMapper);
    verify(permissionStore).releaseRebuildLock(7L, "lock-token");
  }

  /** 未获取重建锁的等待者只轮询缓存，命中后直接返回且不得访问 Mapper。 */
  @Test
  void 未获取锁的等待者命中缓存不访问数据库() {
    when(permissionStore.tryAcquireRebuildLock(7L)).thenReturn(null);
    when(permissionStore.getIfCurrent(7L)).thenReturn(null, null, cache(3L, "/api/orders/{id}"));

    assertTrue(permissionService.hasPermission(7L, "/api/orders/{id}"));

    verifyNoInteractions(
        employeeMapper,
        employeeRoleMapper,
        roleMapper,
        roleResourceMapper,
        systemResourceMapper,
        resourceApiMapper,
        systemApiMapper);
    verify(permissionStore, never()).currentVersion();
  }

  /** 有限轮询结束仍未命中时失败关闭，等待者不得并发查询数据库。 */
  @Test
  void 未获取锁且等待结束时失败关闭() {
    when(permissionStore.tryAcquireRebuildLock(7L)).thenReturn(null);
    when(permissionStore.getIfCurrent(7L)).thenReturn(null);

    assertThrows(
        AuthenticationInfrastructureException.class,
        () -> permissionService.hasPermission(7L, "/api/orders/{id}"));

    verifyNoInteractions(
        employeeMapper,
        employeeRoleMapper,
        roleMapper,
        roleResourceMapper,
        systemResourceMapper,
        resourceApiMapper,
        systemApiMapper);
    verify(permissionStore, never()).currentVersion();
  }

  /** 父资源禁用时不把子资源关联接口写入缓存。 */
  @Test
  void 禁用父资源使子资源无权限() {
    prepareSingleResource(10L);
    when(systemResourceMapper.selectExistingByIds(Set.of(10L)))
        .thenReturn(List.of(resource(10L, 100L, 1)));
    when(systemResourceMapper.selectExistingByIds(Set.of(100L)))
        .thenReturn(List.of(resource(100L, 0L, 0)));

    assertFalse(permissionService.hasPermission(7L, "/api/orders"));

    verify(resourceApiMapper, never()).selectApiIdsByResourceIds(any());
  }

  /** 超级管理员缓存全部启用接口路径，但不能访问未启用路径。 */
  @Test
  void 超级管理员不能绕过禁用接口() {
    when(permissionStore.getIfCurrent(7L)).thenReturn(null);
    when(permissionStore.currentVersion()).thenReturn(5L);
    when(employeeMapper.selectById(7L)).thenReturn(employee(1, 1));
    when(systemApiMapper.selectEnabledPaths()).thenReturn(List.of("/api/enabled"));
    when(systemResourceMapper.selectAllOrdered()).thenReturn(List.of());

    assertFalse(permissionService.hasPermission(7L, "/api/disabled"));

    verifyNoInteractions(employeeRoleMapper, roleMapper, roleResourceMapper);
  }

  /** 禁用员工重建为空权限，且不继续查询角色。 */
  @Test
  void 禁用员工无权限() {
    when(permissionStore.getIfCurrent(7L)).thenReturn(null);
    when(permissionStore.currentVersion()).thenReturn(6L);
    when(employeeMapper.selectById(7L)).thenReturn(employee(0));

    assertFalse(permissionService.hasPermission(7L, "/api/orders"));

    verify(employeeRoleMapper, never()).selectRoleIdsByEmployeeId(anyLong());
    verify(permissionStore).putIfCurrent(org.mockito.ArgumentMatchers.eq(7L), any());
  }

  /** 重建期间全局版本变化时不得使用旧快照放行当前请求。 */
  @Test
  void 重建缓存原子写入失败时当前请求失败关闭() {
    prepareSingleResource(10L);
    when(systemResourceMapper.selectExistingByIds(Set.of(10L)))
        .thenReturn(List.of(resource(10L, 0L, 1)));
    when(resourceApiMapper.selectApiIdsByResourceIds(Set.of(10L))).thenReturn(List.of(20L));
    when(systemApiMapper.selectEnabledPathsByIds(List.of(20L))).thenReturn(List.of("/api/orders"));
    when(permissionStore.putIfCurrent(anyLong(), any(EmployeePermissionCache.class)))
        .thenReturn(false);

    assertThrows(
        AuthenticationInfrastructureException.class,
        () -> permissionService.hasPermission(7L, "/api/orders"));

    verify(permissionStore).putIfCurrent(org.mockito.ArgumentMatchers.eq(7L), any());
  }

  /** 事务中的权限失效只在提交成功后递增版本，避免新版本读取未提交旧数据。 */
  @Test
  void 权限版本在事务提交后递增() {
    when(permissionStore.beginInvalidation()).thenReturn("invalidation-token");
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      permissionService.invalidateAll();

      verify(permissionStore).beginInvalidation();
      verify(permissionStore, never()).completeInvalidation(any());
      TransactionSynchronization synchronization =
          TransactionSynchronizationManager.getSynchronizations().get(0);
      synchronization.afterCommit();
      synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
      verify(permissionStore).completeInvalidation("invalidation-token");
      verify(permissionStore, never()).cancelInvalidation(any());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }

  /** 数据库已提交但 Redis 完成失效失败时保留标记，后续显式重试恢复。 */
  @Test
  void 提交后失效失败由后续重试恢复() {
    when(permissionStore.beginInvalidation()).thenReturn("invalidation-token");
    doThrow(new AuthenticationInfrastructureException("连接失败"))
        .when(permissionStore)
        .completeInvalidation("invalidation-token");
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      permissionService.invalidateAll();
      TransactionSynchronization synchronization =
          TransactionSynchronizationManager.getSynchronizations().get(0);

      assertThrows(AuthenticationInfrastructureException.class, synchronization::afterCommit);
      synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
      verify(permissionStore, never()).cancelInvalidation(any());

      permissionService.retryPendingInvalidation();
      verify(permissionStore).retryPendingInvalidation();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }

  /** 数据库回滚时只清理当前事务的失效 token，不递增版本。 */
  @Test
  void 回滚时清理当前失效标记() {
    when(permissionStore.beginInvalidation()).thenReturn("invalidation-token");
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      permissionService.invalidateAll();
      TransactionSynchronizationManager.getSynchronizations()
          .get(0)
          .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

      verify(permissionStore).cancelInvalidation("invalidation-token");
      verify(permissionStore, never()).completeInvalidation(any());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }

  private void prepareSingleResource(long resourceId) {
    when(permissionStore.getIfCurrent(7L)).thenReturn(null);
    when(permissionStore.currentVersion()).thenReturn(1L);
    when(employeeMapper.selectById(7L)).thenReturn(employee(1));
    when(employeeRoleMapper.selectRoleIdsByEmployeeId(7L)).thenReturn(List.of(1L));
    when(roleMapper.selectEnabledIds(List.of(1L))).thenReturn(List.of(1L));
    when(roleResourceMapper.selectResourceIdsByRoleIds(List.of(1L)))
        .thenReturn(List.of(resourceId));
  }

  private EmployeePermissionCache cache(long version, String... apiPaths) {
    EmployeePermissionCache cache = new EmployeePermissionCache();
    cache.setVersion(version);
    cache.setApiPaths(Set.of(apiPaths));
    cache.setResources(List.of());
    return cache;
  }

  private EmployeeEntity employee(int status) {
    return employee(status, 0);
  }

  private EmployeeEntity employee(int status, int superuser) {
    EmployeeEntity employee = new EmployeeEntity();
    employee.setId(7L);
    employee.setStatus(status);
    employee.setSuperuser(superuser);
    return employee;
  }

  private SystemResourceEntity resource(long id, long parentId, int status) {
    SystemResourceEntity resource = new SystemResourceEntity();
    resource.setId(id);
    resource.setParentId(parentId);
    resource.setType("ACTION");
    resource.setName("测试资源");
    resource.setStatus(status);
    return resource;
  }
}
