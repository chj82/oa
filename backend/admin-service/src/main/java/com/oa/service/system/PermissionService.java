package com.oa.service.system;

import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.system.cache.EmployeePermissionCache;
import com.oa.common.model.system.enums.SystemStatus;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 使用 Redis 缓存和语义化单表查询判断员工接口权限。 */
@Service
public class PermissionService {
  private static final int MAX_RESOURCE_DEPTH = 64;
  private static final int REBUILD_WAIT_ATTEMPTS = 5;
  private static final long REBUILD_WAIT_NANOS = 10_000_000L;

  private final EmployeeMapper employeeMapper;
  private final EmployeeRoleMapper employeeRoleMapper;
  private final RoleMapper roleMapper;
  private final RoleResourceMapper roleResourceMapper;
  private final SystemResourceMapper systemResourceMapper;
  private final ResourceApiMapper resourceApiMapper;
  private final SystemApiMapper systemApiMapper;
  private final StringRedisPermissionStore permissionStore;

  public PermissionService(
      EmployeeMapper employeeMapper,
      EmployeeRoleMapper employeeRoleMapper,
      RoleMapper roleMapper,
      RoleResourceMapper roleResourceMapper,
      SystemResourceMapper systemResourceMapper,
      ResourceApiMapper resourceApiMapper,
      SystemApiMapper systemApiMapper,
      StringRedisPermissionStore permissionStore) {
    this.employeeMapper = employeeMapper;
    this.employeeRoleMapper = employeeRoleMapper;
    this.roleMapper = roleMapper;
    this.roleResourceMapper = roleResourceMapper;
    this.systemResourceMapper = systemResourceMapper;
    this.resourceApiMapper = resourceApiMapper;
    this.systemApiMapper = systemApiMapper;
    this.permissionStore = permissionStore;
  }

  /** 判断员工是否可以访问指定路由模板，缓存命中时不访问数据库。 */
  public boolean hasPermission(long employeeId, String apiPath) {
    EmployeePermissionCache cache = permissionStore.getIfCurrent(employeeId);
    if (cache == null) {
      cache = loadOrRebuild(employeeId);
    }
    return cache.getApiPaths().contains(apiPath);
  }

  /** 原子递增全局版本，使全部员工权限缓存失效。 */
  public void invalidateAll() {
    String invalidationToken = permissionStore.beginInvalidation();
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              permissionStore.completeInvalidation(invalidationToken);
            }

            @Override
            public void afterCompletion(int status) {
              if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                permissionStore.cancelInvalidation(invalidationToken);
              }
            }
          });
      return;
    }
    permissionStore.completeInvalidation(invalidationToken);
  }

  /** 恢复上次数据库提交后遗留的权限失效标记。 */
  public void retryPendingInvalidation() {
    permissionStore.retryPendingInvalidation();
  }

  private EmployeePermissionCache loadOrRebuild(long employeeId) {
    String lockToken = permissionStore.tryAcquireRebuildLock(employeeId);
    if (lockToken == null) {
      return waitForRebuiltCache(employeeId);
    }
    try {
      EmployeePermissionCache cache = permissionStore.getIfCurrent(employeeId);
      if (cache != null) {
        return cache;
      }
      cache = rebuild(employeeId, permissionStore.currentVersion());
      permissionStore.put(employeeId, cache);
      return cache;
    } finally {
      permissionStore.releaseRebuildLock(employeeId, lockToken);
    }
  }

  private EmployeePermissionCache waitForRebuiltCache(long employeeId) {
    for (int attempt = 0; attempt < REBUILD_WAIT_ATTEMPTS; attempt++) {
      LockSupport.parkNanos(REBUILD_WAIT_NANOS);
      if (Thread.currentThread().isInterrupted()) {
        throw unavailable();
      }
      EmployeePermissionCache cache = permissionStore.getIfCurrent(employeeId);
      if (cache != null) {
        return cache;
      }
    }
    throw unavailable();
  }

  private AuthenticationInfrastructureException unavailable() {
    return new AuthenticationInfrastructureException("认证服务暂不可用");
  }

  private EmployeePermissionCache rebuild(long employeeId, long version) {
    EmployeePermissionCache cache = new EmployeePermissionCache();
    cache.setVersion(version);
    cache.setApiPaths(loadEnabledApiPaths(employeeId));
    return cache;
  }

  private Set<String> loadEnabledApiPaths(long employeeId) {
    EmployeeEntity employee = employeeMapper.selectById(employeeId);
    if (employee == null || employee.getStatus() != SystemStatus.ENABLED.getCode()) {
      return Set.of();
    }
    if (employee.getSuperuser() == 1) {
      return new LinkedHashSet<>(systemApiMapper.selectEnabledPaths());
    }

    List<Long> roleIds = employeeRoleMapper.selectRoleIdsByEmployeeId(employeeId);
    if (roleIds.isEmpty()) {
      return Set.of();
    }
    List<Long> enabledRoleIds = roleMapper.selectEnabledIds(roleIds);
    if (enabledRoleIds.isEmpty()) {
      return Set.of();
    }
    List<Long> resourceIds = roleResourceMapper.selectResourceIdsByRoleIds(enabledRoleIds);
    if (resourceIds.isEmpty()) {
      return Set.of();
    }

    Set<Long> enabledResourceIds = enabledResourcesWithAncestors(resourceIds);
    if (enabledResourceIds.isEmpty()) {
      return Set.of();
    }
    List<Long> apiIds = resourceApiMapper.selectApiIdsByResourceIds(enabledResourceIds);
    return new LinkedHashSet<>(systemApiMapper.selectEnabledPathsByIds(apiIds));
  }

  private Set<Long> enabledResourcesWithAncestors(Collection<Long> resourceIds) {
    Map<Long, Long> pending = new LinkedHashMap<>();
    for (Long resourceId : new LinkedHashSet<>(resourceIds)) {
      pending.put(resourceId, resourceId);
    }
    Set<Long> enabled = new LinkedHashSet<>();

    for (int depth = 0; depth < MAX_RESOURCE_DEPTH && !pending.isEmpty(); depth++) {
      Set<Long> currentIds = new LinkedHashSet<>(pending.values());
      Map<Long, SystemResourceEntity> currentResources =
          systemResourceMapper.selectExistingByIds(currentIds).stream()
              .collect(Collectors.toMap(SystemResourceEntity::getId, Function.identity()));
      Map<Long, Long> next = new LinkedHashMap<>();
      for (Map.Entry<Long, Long> entry : pending.entrySet()) {
        SystemResourceEntity current = currentResources.get(entry.getValue());
        if (current == null || current.getStatus() != SystemStatus.ENABLED.getCode()) {
          continue;
        }
        if (current.getParentId() == 0) {
          enabled.add(entry.getKey());
        } else {
          next.put(entry.getKey(), current.getParentId());
        }
      }
      pending = next;
    }
    return enabled;
  }
}
