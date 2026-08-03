package com.oa.service.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.model.system.cache.EmployeeAuthorizationCache;
import com.oa.common.model.system.cache.ResourcePermissionNode;
import com.oa.common.model.system.cache.ResourcePermissionSnapshot;
import com.oa.common.model.system.cache.RoleAuthorizationCache;
import com.oa.common.model.system.vo.ResourceVO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 员工权限请求内合成测试。 */
class PermissionServiceTest {

  /** 禁用员工没有任何权限。 */
  @Test
  void 禁用员工返回空权限() {
    AuthorizationRelationService relations = mock(AuthorizationRelationService.class);
    PermissionSnapshotService snapshots = mock(PermissionSnapshotService.class);
    when(relations.currentEmployee(7L)).thenReturn(employee(0, false, List.of(10L)));
    PermissionService service = new PermissionService(relations, snapshots);

    assertFalse(service.hasPermission(7L, "/api/a"));
    assertEquals(List.of(), service.getResources(7L));

    verify(snapshots, never()).currentSnapshot();
  }

  /** 超级管理员直接使用快照全部资源和接口，不读取角色。 */
  @Test
  void 超级管理员使用完整快照() {
    AuthorizationRelationService relations = mock(AuthorizationRelationService.class);
    PermissionSnapshotService snapshots = mock(PermissionSnapshotService.class);
    when(relations.currentEmployee(7L)).thenReturn(employee(1, true, List.of(10L)));
    when(snapshots.currentSnapshot()).thenReturn(snapshot());
    PermissionService service = new PermissionService(relations, snapshots);

    assertTrue(service.hasPermission(7L, "/api/a"));
    assertEquals(
        2, service.getResources(7L).size() + service.getResources(7L).get(0).getChildren().size());

    verify(relations, never()).currentRoles(org.mockito.ArgumentMatchers.anyCollection());
  }

  /** 普通员工批量读取角色，忽略禁用角色并合并有效资源权限。 */
  @Test
  void 普通员工合并启用角色资源() {
    AuthorizationRelationService relations = mock(AuthorizationRelationService.class);
    PermissionSnapshotService snapshots = mock(PermissionSnapshotService.class);
    when(relations.currentEmployee(7L)).thenReturn(employee(1, false, List.of(10L, 20L)));
    when(relations.currentRoles(List.of(10L, 20L)))
        .thenReturn(
            Map.of(
                10L, role(10L, 1, List.of(2L)),
                20L, role(20L, 0, List.of(1L))));
    when(snapshots.currentSnapshot()).thenReturn(snapshot());
    PermissionService service = new PermissionService(relations, snapshots);

    assertTrue(service.hasPermission(7L, "/api/b"));
    assertFalse(service.hasPermission(7L, "/api/a"));
    List<ResourceVO> tree = service.getResources(7L);
    assertEquals(1L, tree.get(0).getId());
    assertEquals(2L, tree.get(0).getChildren().get(0).getId());
  }

  /** 每次返回独立资源树，调用方修改不得污染 JVM 快照。 */
  @Test
  void 返回资源树互相隔离() {
    AuthorizationRelationService relations = mock(AuthorizationRelationService.class);
    PermissionSnapshotService snapshots = mock(PermissionSnapshotService.class);
    when(relations.currentEmployee(7L)).thenReturn(employee(1, true, List.of()));
    when(snapshots.currentSnapshot()).thenReturn(snapshot());
    PermissionService service = new PermissionService(relations, snapshots);

    List<ResourceVO> first = service.getResources(7L);
    first.clear();

    assertEquals(1, service.getResources(7L).size());
  }

  private EmployeeAuthorizationCache employee(int status, boolean superuser, List<Long> roleIds) {
    EmployeeAuthorizationCache employee = new EmployeeAuthorizationCache();
    employee.setEmployeeId(7L);
    employee.setStatus(status);
    employee.setSuperuser(superuser);
    employee.setRoleIds(roleIds);
    return employee;
  }

  private RoleAuthorizationCache role(long roleId, int status, List<Long> resourceIds) {
    RoleAuthorizationCache role = new RoleAuthorizationCache();
    role.setRoleId(roleId);
    role.setStatus(status);
    role.setResourceIds(resourceIds);
    return role;
  }

  private ResourcePermissionSnapshot snapshot() {
    Map<Long, ResourcePermissionNode> nodes = new LinkedHashMap<>();
    nodes.put(1L, node(1L, 0L, Set.of("/api/a")));
    nodes.put(2L, node(2L, 1L, Set.of("/api/b")));
    ResourcePermissionSnapshot snapshot = new ResourcePermissionSnapshot();
    snapshot.setVersion(1L);
    snapshot.setNodes(nodes);
    snapshot.setAllEnabledApiPaths(Set.of("/api/a", "/api/b"));
    return snapshot;
  }

  private ResourcePermissionNode node(long id, long parentId, Set<String> apiPaths) {
    ResourcePermissionNode node = new ResourcePermissionNode();
    node.setId(id);
    node.setParentId(parentId);
    node.setType(id == 1L ? "DIRECTORY" : "MENU");
    node.setName("资源" + id);
    node.setCode("RESOURCE_" + id);
    node.setPath("/resource/" + id);
    node.setIcon("menu");
    node.setSortOrder((int) id);
    node.setVisible(true);
    node.setStatus(1);
    node.setApiPaths(apiPaths);
    return node;
  }
}
