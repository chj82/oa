package com.oa.service.system;

import com.oa.common.model.system.cache.EmployeeAuthorizationCache;
import com.oa.common.model.system.cache.RoleAuthorizationCache;
import com.oa.dao.system.EmployeeMapper;
import com.oa.dao.system.EmployeeRoleMapper;
import com.oa.dao.system.RoleMapper;
import com.oa.dao.system.RoleResourceMapper;
import com.oa.entity.system.EmployeeEntity;
import com.oa.entity.system.RoleEntity;
import com.oa.entity.system.RoleResourceEntity;
import com.oa.service.system.store.StringRedisAuthorizationStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 通过 Cache Aside 加载员工和角色授权关系。 */
@Service
public class AuthorizationRelationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationRelationService.class);
  private static final int ROLE_QUERY_BATCH_SIZE = 500;

  private final EmployeeMapper employeeMapper;
  private final EmployeeRoleMapper employeeRoleMapper;
  private final RoleMapper roleMapper;
  private final RoleResourceMapper roleResourceMapper;
  private final StringRedisAuthorizationStore authorizationStore;

  public AuthorizationRelationService(
      EmployeeMapper employeeMapper,
      EmployeeRoleMapper employeeRoleMapper,
      RoleMapper roleMapper,
      RoleResourceMapper roleResourceMapper,
      StringRedisAuthorizationStore authorizationStore) {
    this.employeeMapper = employeeMapper;
    this.employeeRoleMapper = employeeRoleMapper;
    this.roleMapper = roleMapper;
    this.roleResourceMapper = roleResourceMapper;
    this.authorizationStore = authorizationStore;
  }

  /** 获取员工授权关系，缓存缺失时查询单表并回填。 */
  public EmployeeAuthorizationCache currentEmployee(long employeeId) {
    EmployeeAuthorizationCache cached = authorizationStore.getEmployee(employeeId);
    if (cached != null) {
      return cached;
    }
    EmployeeEntity employee = employeeMapper.selectById(employeeId);
    EmployeeAuthorizationCache loaded = new EmployeeAuthorizationCache();
    loaded.setEmployeeId(employeeId);
    if (employee == null) {
      loaded.setStatus(0);
      loaded.setSuperuser(false);
      loaded.setRoleIds(List.of());
    } else {
      loaded.setStatus(employee.getStatus());
      loaded.setSuperuser(employee.getSuperuser() == 1);
      loaded.setRoleIds(employeeRoleMapper.selectRoleIdsByEmployeeId(employeeId));
    }
    authorizationStore.putEmployee(loaded);
    return loaded;
  }

  /** 批量获取角色授权关系，仅对缓存缺失部分查询数据库。 */
  public Map<Long, RoleAuthorizationCache> currentRoles(Collection<Long> roleIds) {
    List<Long> orderedIds = new ArrayList<>(new LinkedHashSet<>(roleIds));
    Map<Long, RoleAuthorizationCache> cached = authorizationStore.multiGetRoles(orderedIds);
    Map<Long, RoleAuthorizationCache> result = new LinkedHashMap<>();
    List<Long> missingIds = new ArrayList<>();
    for (Long roleId : orderedIds) {
      RoleAuthorizationCache role = cached.get(roleId);
      if (role == null) {
        missingIds.add(roleId);
      } else {
        result.put(roleId, role);
      }
    }
    for (int start = 0; start < missingIds.size(); start += ROLE_QUERY_BATCH_SIZE) {
      int end = Math.min(start + ROLE_QUERY_BATCH_SIZE, missingIds.size());
      loadRoleBatch(missingIds.subList(start, end), result);
    }
    Map<Long, RoleAuthorizationCache> orderedResult = new LinkedHashMap<>();
    for (Long roleId : orderedIds) {
      RoleAuthorizationCache role = result.get(roleId);
      if (role != null) {
        orderedResult.put(roleId, role);
      }
    }
    return orderedResult;
  }

  /** 数据库提交后精准删除员工授权缓存。 */
  public void evictEmployeeAfterCommit(long employeeId) {
    afterCommit(() -> deleteEmployee(employeeId));
  }

  /** 数据库提交后精准删除角色授权缓存。 */
  public void evictRoleAfterCommit(long roleId) {
    afterCommit(() -> deleteRole(roleId));
  }

  private void loadRoleBatch(List<Long> roleIds, Map<Long, RoleAuthorizationCache> destination) {
    List<RoleEntity> roles = roleMapper.selectExistingByIds(roleIds);
    Map<Long, List<Long>> resourceIdsByRole = new LinkedHashMap<>();
    for (RoleResourceEntity relation : roleResourceMapper.selectRelationsByRoleIds(roleIds)) {
      resourceIdsByRole
          .computeIfAbsent(relation.getRoleId(), ignored -> new ArrayList<>())
          .add(relation.getResourceId());
    }
    List<RoleAuthorizationCache> loaded = new ArrayList<>();
    for (RoleEntity role : roles) {
      RoleAuthorizationCache cache = new RoleAuthorizationCache();
      cache.setRoleId(role.getId());
      cache.setStatus(role.getStatus());
      cache.setResourceIds(resourceIdsByRole.getOrDefault(role.getId(), List.of()));
      loaded.add(cache);
      destination.put(role.getId(), cache);
    }
    authorizationStore.putRoles(loaded);
  }

  private void afterCommit(Runnable operation) {
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              operation.run();
            }
          });
    } else {
      operation.run();
    }
  }

  private void deleteEmployee(long employeeId) {
    try {
      authorizationStore.deleteEmployee(employeeId);
    } catch (RuntimeException exception) {
      LOGGER.error("删除员工授权缓存失败，员工ID={}", employeeId, exception);
    }
  }

  private void deleteRole(long roleId) {
    try {
      authorizationStore.deleteRole(roleId);
    } catch (RuntimeException exception) {
      LOGGER.error("删除角色授权缓存失败，角色ID={}", roleId, exception);
    }
  }
}
