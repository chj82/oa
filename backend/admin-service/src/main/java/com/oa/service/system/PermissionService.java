package com.oa.service.system;

import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.system.cache.EmployeePermissionCache;
import com.oa.common.model.system.enums.ResourceType;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.model.system.vo.ResourceVO;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
    EmployeePermissionCache cache = currentPermission(employeeId);
    return cache.getApiPaths().contains(apiPath);
  }

  /** 获取员工有效资源树，缓存命中时不访问数据库。 */
  public List<ResourceVO> getResources(long employeeId) {
    return currentPermission(employeeId).getResources();
  }

  private EmployeePermissionCache currentPermission(long employeeId) {
    EmployeePermissionCache cache = permissionStore.getIfCurrent(employeeId);
    return cache == null ? loadOrRebuild(employeeId) : cache;
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
      if (!permissionStore.putIfCurrent(employeeId, cache)) {
        throw unavailable();
      }
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
    cache.setResources(loadEffectiveResources(employeeId));
    return cache;
  }

  private List<ResourceVO> loadEffectiveResources(long employeeId) {
    EmployeeEntity employee = employeeMapper.selectById(employeeId);
    if (employee == null || employee.getStatus() != SystemStatus.ENABLED.getCode()) {
      return List.of();
    }
    if (employee.getSuperuser() == 1) {
      return buildEnabledTree(systemResourceMapper.selectAllOrdered());
    }
    List<Long> roleIds = employeeRoleMapper.selectRoleIdsByEmployeeId(employeeId);
    if (roleIds.isEmpty()) {
      return List.of();
    }
    List<Long> enabledRoleIds = roleMapper.selectEnabledIds(roleIds);
    if (enabledRoleIds.isEmpty()) {
      return List.of();
    }
    List<Long> resourceIds = roleResourceMapper.selectResourceIdsByRoleIds(enabledRoleIds);
    return buildAuthorizedTree(resourceIds);
  }

  private List<ResourceVO> buildAuthorizedTree(Collection<Long> resourceIds) {
    Map<Long, Long> pending = new LinkedHashMap<>();
    Map<Long, List<SystemResourceEntity>> paths = new LinkedHashMap<>();
    for (Long resourceId : new LinkedHashSet<>(resourceIds)) {
      pending.put(resourceId, resourceId);
      paths.put(resourceId, new ArrayList<>());
    }
    Set<Long> authorizedIds = new LinkedHashSet<>();
    Map<Long, SystemResourceEntity> entities = new HashMap<>();
    for (int depth = 0; depth < MAX_RESOURCE_DEPTH && !pending.isEmpty(); depth++) {
      Map<Long, SystemResourceEntity> currentResources =
          systemResourceMapper.selectExistingByIds(new LinkedHashSet<>(pending.values())).stream()
              .collect(Collectors.toMap(SystemResourceEntity::getId, Function.identity()));
      Map<Long, Long> next = new LinkedHashMap<>();
      for (Map.Entry<Long, Long> entry : pending.entrySet()) {
        SystemResourceEntity current = currentResources.get(entry.getValue());
        if (current == null || current.getStatus() != SystemStatus.ENABLED.getCode()) {
          continue;
        }
        paths.get(entry.getKey()).add(current);
        entities.put(current.getId(), current);
        if (current.getParentId() == 0) {
          paths.get(entry.getKey()).forEach(resource -> authorizedIds.add(resource.getId()));
        } else {
          next.put(entry.getKey(), current.getParentId());
        }
      }
      pending = next;
    }
    return buildTree(
        authorizedIds.stream().map(entities::get).filter(java.util.Objects::nonNull).toList());
  }

  private List<ResourceVO> buildEnabledTree(List<SystemResourceEntity> resources) {
    Map<Long, SystemResourceEntity> entities =
        resources.stream()
            .filter(resource -> resource.getStatus() == SystemStatus.ENABLED.getCode())
            .collect(Collectors.toMap(SystemResourceEntity::getId, Function.identity()));
    List<SystemResourceEntity> effective =
        entities.values().stream()
            .filter(resource -> hasEnabledAncestors(resource, entities))
            .toList();
    return buildTree(effective);
  }

  private boolean hasEnabledAncestors(
      SystemResourceEntity resource, Map<Long, SystemResourceEntity> entities) {
    long parentId = resource.getParentId();
    for (int depth = 0; parentId != 0 && depth < MAX_RESOURCE_DEPTH; depth++) {
      SystemResourceEntity parent = entities.get(parentId);
      if (parent == null) {
        return false;
      }
      parentId = parent.getParentId();
    }
    return parentId == 0;
  }

  private List<ResourceVO> buildTree(Collection<SystemResourceEntity> resources) {
    List<SystemResourceEntity> ordered =
        resources.stream()
            .sorted(
                Comparator.comparingInt(SystemResourceEntity::getSortOrder)
                    .thenComparingLong(SystemResourceEntity::getId))
            .toList();
    Map<Long, ResourceVO> nodes = new LinkedHashMap<>();
    for (SystemResourceEntity resource : ordered) {
      nodes.put(resource.getId(), toResource(resource));
    }
    List<ResourceVO> roots = new ArrayList<>();
    for (SystemResourceEntity resource : ordered) {
      ResourceVO node = nodes.get(resource.getId());
      ResourceVO parent = nodes.get(resource.getParentId());
      if (resource.getParentId() == 0 || parent == null) {
        roots.add(node);
      } else {
        parent.getChildren().add(node);
      }
    }
    return roots;
  }

  private ResourceVO toResource(SystemResourceEntity resource) {
    ResourceVO result = new ResourceVO();
    result.setId(resource.getId());
    result.setParentId(resource.getParentId());
    result.setType(ResourceType.valueOf(resource.getType()));
    result.setName(resource.getName());
    result.setCode(resource.getCode());
    result.setPath(resource.getPath());
    result.setIcon(resource.getIcon());
    result.setSortOrder(resource.getSortOrder());
    result.setVisible(resource.getVisible() == 1);
    result.setStatus(SystemStatus.ENABLED);
    result.setChildren(new ArrayList<>());
    result.setCreatedAt(resource.getCreatedAt());
    result.setUpdatedAt(resource.getUpdatedAt());
    return result;
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
