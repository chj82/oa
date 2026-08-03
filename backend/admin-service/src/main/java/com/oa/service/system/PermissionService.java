package com.oa.service.system;

import com.oa.common.model.system.cache.EmployeeAuthorizationCache;
import com.oa.common.model.system.cache.ResourcePermissionNode;
import com.oa.common.model.system.cache.ResourcePermissionSnapshot;
import com.oa.common.model.system.cache.RoleAuthorizationCache;
import com.oa.common.model.system.enums.ResourceType;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.model.system.permission.ResolvedEmployeePermission;
import com.oa.common.model.system.vo.ResourceVO;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 使用授权关系缓存和 JVM 资源快照在请求内合成员工权限。 */
@Service
public class PermissionService {
  private static final int MAX_RESOURCE_DEPTH = 64;

  private final AuthorizationRelationService authorizationRelationService;
  private final PermissionSnapshotService permissionSnapshotService;

  public PermissionService(
      AuthorizationRelationService authorizationRelationService,
      PermissionSnapshotService permissionSnapshotService) {
    this.authorizationRelationService = authorizationRelationService;
    this.permissionSnapshotService = permissionSnapshotService;
  }

  /** 判断员工是否可以访问指定接口路径。 */
  public boolean hasPermission(long employeeId, String apiPath) {
    return resolve(employeeId).getApiPaths().contains(apiPath);
  }

  /** 获取员工可访问的独立资源树。 */
  public List<ResourceVO> getResources(long employeeId) {
    return resolve(employeeId).getResources();
  }

  /** 旧写服务迁移期间的兼容入口，迁移完成后删除。 */
  public void invalidateAll() {
    permissionSnapshotService.incrementPersistentVersion();
  }

  private ResolvedEmployeePermission resolve(long employeeId) {
    EmployeeAuthorizationCache employee = authorizationRelationService.currentEmployee(employeeId);
    if (employee.getStatus() != SystemStatus.ENABLED.getCode()) {
      return resolved(Set.of(), List.of());
    }
    ResourcePermissionSnapshot snapshot = permissionSnapshotService.currentSnapshot();
    if (employee.getSuperuser()) {
      return resolved(snapshot.getAllEnabledApiPaths(), buildTree(snapshot.getNodes().values()));
    }
    Map<Long, RoleAuthorizationCache> roles =
        authorizationRelationService.currentRoles(employee.getRoleIds());
    Set<Long> authorizedResourceIds = new LinkedHashSet<>();
    for (RoleAuthorizationCache role : roles.values()) {
      if (role.getStatus() == SystemStatus.ENABLED.getCode()) {
        authorizedResourceIds.addAll(role.getResourceIds());
      }
    }
    Set<Long> treeResourceIds = collectEffectiveTreeIds(authorizedResourceIds, snapshot.getNodes());
    Set<String> apiPaths = new LinkedHashSet<>();
    for (Long resourceId : authorizedResourceIds) {
      ResourcePermissionNode node = snapshot.getNodes().get(resourceId);
      if (node != null && treeResourceIds.contains(resourceId)) {
        apiPaths.addAll(node.getApiPaths());
      }
    }
    List<ResourcePermissionNode> treeNodes =
        treeResourceIds.stream().map(snapshot.getNodes()::get).toList();
    return resolved(apiPaths, buildTree(treeNodes));
  }

  private Set<Long> collectEffectiveTreeIds(
      Set<Long> authorizedIds, Map<Long, ResourcePermissionNode> nodes) {
    Set<Long> result = new LinkedHashSet<>();
    for (Long authorizedId : authorizedIds) {
      ResourcePermissionNode current = nodes.get(authorizedId);
      List<Long> path = new ArrayList<>();
      for (int depth = 0; current != null && depth < MAX_RESOURCE_DEPTH; depth++) {
        path.add(current.getId());
        if (current.getParentId() == 0) {
          result.addAll(path);
          break;
        }
        current = nodes.get(current.getParentId());
      }
    }
    return result;
  }

  private List<ResourceVO> buildTree(Collection<ResourcePermissionNode> resources) {
    List<ResourcePermissionNode> ordered =
        resources.stream()
            .sorted(
                Comparator.comparingInt(ResourcePermissionNode::getSortOrder)
                    .thenComparingLong(ResourcePermissionNode::getId))
            .toList();
    Map<Long, ResourceVO> nodes = new LinkedHashMap<>();
    for (ResourcePermissionNode resource : ordered) {
      nodes.put(resource.getId(), toResource(resource));
    }
    List<ResourceVO> roots = new ArrayList<>();
    for (ResourcePermissionNode resource : ordered) {
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

  private ResourceVO toResource(ResourcePermissionNode resource) {
    ResourceVO result = new ResourceVO();
    result.setId(resource.getId());
    result.setParentId(resource.getParentId());
    result.setType(ResourceType.valueOf(resource.getType()));
    result.setName(resource.getName());
    result.setCode(resource.getCode());
    result.setPath(resource.getPath());
    result.setIcon(resource.getIcon());
    result.setSortOrder(resource.getSortOrder());
    result.setVisible(resource.getVisible());
    result.setStatus(
        resource.getStatus() == SystemStatus.ENABLED.getCode()
            ? SystemStatus.ENABLED
            : SystemStatus.DISABLED);
    result.setChildren(new ArrayList<>());
    result.setCreatedAt(resource.getCreatedAt());
    result.setUpdatedAt(resource.getUpdatedAt());
    return result;
  }

  private ResolvedEmployeePermission resolved(Set<String> apiPaths, List<ResourceVO> resources) {
    ResolvedEmployeePermission result = new ResolvedEmployeePermission();
    result.setApiPaths(apiPaths);
    result.setResources(resources);
    return result;
  }
}
