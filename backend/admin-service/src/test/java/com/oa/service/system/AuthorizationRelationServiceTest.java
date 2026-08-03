package com.oa.service.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 员工与角色授权关系 Cache Aside 测试。 */
class AuthorizationRelationServiceTest {

  /** 员工缓存命中时不得访问数据库。 */
  @Test
  void 员工缓存命中不查数据库() {
    Dependencies dependencies = dependencies();
    EmployeeAuthorizationCache cached = new EmployeeAuthorizationCache();
    cached.setEmployeeId(7L);
    cached.setRoleIds(List.of(10L));
    when(dependencies.store.getEmployee(7L)).thenReturn(cached);

    EmployeeAuthorizationCache result = dependencies.service.currentEmployee(7L);

    assertEquals(List.of(10L), result.getRoleIds());
    verify(dependencies.employeeMapper, never()).selectById(7L);
  }

  /** 员工缓存缺失时按单表查询并回填关系。 */
  @Test
  void 员工缓存缺失时加载并回填() {
    Dependencies dependencies = dependencies();
    EmployeeEntity employee = new EmployeeEntity();
    employee.setId(7L);
    employee.setStatus(1);
    employee.setSuperuser(0);
    when(dependencies.employeeMapper.selectById(7L)).thenReturn(employee);
    when(dependencies.employeeRoleMapper.selectRoleIdsByEmployeeId(7L))
        .thenReturn(List.of(10L, 20L));

    EmployeeAuthorizationCache result = dependencies.service.currentEmployee(7L);

    assertEquals(List.of(10L, 20L), result.getRoleIds());
    verify(dependencies.store).putEmployee(result);
  }

  /** 多个角色缺失时只批量查询一次角色和角色资源关联。 */
  @Test
  void 角色缓存缺失批量加载() {
    Dependencies dependencies = dependencies();
    RoleAuthorizationCache cached = new RoleAuthorizationCache();
    cached.setRoleId(10L);
    cached.setStatus(1);
    cached.setResourceIds(List.of(100L));
    when(dependencies.store.multiGetRoles(List.of(10L, 20L, 30L))).thenReturn(Map.of(10L, cached));
    when(dependencies.roleMapper.selectExistingByIds(List.of(20L, 30L)))
        .thenReturn(List.of(role(20L, 1), role(30L, 0)));
    when(dependencies.roleResourceMapper.selectRelationsByRoleIds(List.of(20L, 30L)))
        .thenReturn(List.of(relation(20L, 200L), relation(20L, 201L)));

    Map<Long, RoleAuthorizationCache> result =
        dependencies.service.currentRoles(List.of(10L, 20L, 30L));

    assertEquals(List.of(200L, 201L), result.get(20L).getResourceIds());
    assertEquals(0, result.get(30L).getStatus());
    verify(dependencies.store).putRoles(org.mockito.ArgumentMatchers.anyCollection());
  }

  private Dependencies dependencies() {
    EmployeeMapper employeeMapper = mock(EmployeeMapper.class);
    EmployeeRoleMapper employeeRoleMapper = mock(EmployeeRoleMapper.class);
    RoleMapper roleMapper = mock(RoleMapper.class);
    RoleResourceMapper roleResourceMapper = mock(RoleResourceMapper.class);
    StringRedisAuthorizationStore store = mock(StringRedisAuthorizationStore.class);
    AuthorizationRelationService service =
        new AuthorizationRelationService(
            employeeMapper, employeeRoleMapper, roleMapper, roleResourceMapper, store);
    return new Dependencies(
        employeeMapper, employeeRoleMapper, roleMapper, roleResourceMapper, store, service);
  }

  private RoleEntity role(long id, int status) {
    RoleEntity role = new RoleEntity();
    role.setId(id);
    role.setStatus(status);
    return role;
  }

  private RoleResourceEntity relation(long roleId, long resourceId) {
    RoleResourceEntity relation = new RoleResourceEntity();
    relation.setRoleId(roleId);
    relation.setResourceId(resourceId);
    return relation;
  }

  private static class Dependencies {
    private final EmployeeMapper employeeMapper;
    private final EmployeeRoleMapper employeeRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleResourceMapper roleResourceMapper;
    private final StringRedisAuthorizationStore store;
    private final AuthorizationRelationService service;

    private Dependencies(
        EmployeeMapper employeeMapper,
        EmployeeRoleMapper employeeRoleMapper,
        RoleMapper roleMapper,
        RoleResourceMapper roleResourceMapper,
        StringRedisAuthorizationStore store,
        AuthorizationRelationService service) {
      this.employeeMapper = employeeMapper;
      this.employeeRoleMapper = employeeRoleMapper;
      this.roleMapper = roleMapper;
      this.roleResourceMapper = roleResourceMapper;
      this.store = store;
      this.service = service;
    }
  }
}
