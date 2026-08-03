package com.oa.service.system;

import com.oa.common.model.system.cache.ResourcePermissionNode;
import com.oa.common.model.system.cache.ResourcePermissionSnapshot;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.dao.system.ResourceApiMapper;
import com.oa.dao.system.SystemApiMapper;
import com.oa.dao.system.SystemResourceMapper;
import com.oa.dao.system.SystemVersionMapper;
import com.oa.entity.system.ResourceApiEntity;
import com.oa.entity.system.SystemApiEntity;
import com.oa.entity.system.SystemResourceEntity;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 从数据库一致性视图加载 JVM 资源权限快照。 */
@Service
public class PermissionSnapshotLoader {
  private static final int MAX_RESOURCE_DEPTH = 64;
  private static final String PERMISSION_SNAPSHOT_VERSION_CODE = "permission_snapshot";

  private final SystemVersionMapper versionMapper;
  private final SystemResourceMapper resourceMapper;
  private final ResourceApiMapper relationMapper;
  private final SystemApiMapper apiMapper;

  public PermissionSnapshotLoader(
      SystemVersionMapper versionMapper,
      SystemResourceMapper resourceMapper,
      ResourceApiMapper relationMapper,
      SystemApiMapper apiMapper) {
    this.versionMapper = versionMapper;
    this.resourceMapper = resourceMapper;
    this.relationMapper = relationMapper;
    this.apiMapper = apiMapper;
  }

  /** 在同一个只读事务中构建并冻结资源权限快照。 */
  @Transactional(readOnly = true)
  public ResourcePermissionSnapshot load() {
    long version = versionMapper.selectVersion(PERMISSION_SNAPSHOT_VERSION_CODE);
    List<SystemResourceEntity> resources = resourceMapper.selectAllOrdered();
    List<ResourceApiEntity> relations = relationMapper.selectAllRelations();
    List<SystemApiEntity> enabledApis = apiMapper.selectAllEnabled();

    Map<Long, SystemResourceEntity> enabledResources =
        resources.stream()
            .filter(resource -> resource.getStatus() == SystemStatus.ENABLED.getCode())
            .collect(Collectors.toMap(SystemResourceEntity::getId, Function.identity()));
    Set<Long> effectiveResourceIds =
        enabledResources.values().stream()
            .filter(resource -> hasEnabledAncestors(resource, enabledResources))
            .map(SystemResourceEntity::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Map<Long, String> enabledApiPaths =
        enabledApis.stream()
            .collect(Collectors.toMap(SystemApiEntity::getId, SystemApiEntity::getPath));
    Map<Long, Set<String>> pathsByResource = new HashMap<>();
    for (ResourceApiEntity relation : relations) {
      String path = enabledApiPaths.get(relation.getApiId());
      if (path != null && effectiveResourceIds.contains(relation.getResourceId())) {
        pathsByResource
            .computeIfAbsent(relation.getResourceId(), ignored -> new LinkedHashSet<>())
            .add(path);
      }
    }
    Map<Long, ResourcePermissionNode> nodes = new LinkedHashMap<>();
    for (SystemResourceEntity resource : resources) {
      if (effectiveResourceIds.contains(resource.getId())) {
        nodes.put(
            resource.getId(),
            toNode(resource, pathsByResource.getOrDefault(resource.getId(), Set.of())));
      }
    }
    ResourcePermissionSnapshot snapshot = new ResourcePermissionSnapshot();
    snapshot.setVersion(version);
    snapshot.setNodes(nodes);
    snapshot.setAllEnabledApiPaths(new LinkedHashSet<>(enabledApiPaths.values()));
    return snapshot;
  }

  private boolean hasEnabledAncestors(
      SystemResourceEntity resource, Map<Long, SystemResourceEntity> enabledResources) {
    long parentId = resource.getParentId();
    for (int depth = 0; parentId != 0 && depth < MAX_RESOURCE_DEPTH; depth++) {
      SystemResourceEntity parent = enabledResources.get(parentId);
      if (parent == null) {
        return false;
      }
      parentId = parent.getParentId();
    }
    return parentId == 0;
  }

  private ResourcePermissionNode toNode(SystemResourceEntity resource, Set<String> apiPaths) {
    ResourcePermissionNode node = new ResourcePermissionNode();
    node.setId(resource.getId());
    node.setParentId(resource.getParentId());
    node.setType(resource.getType());
    node.setName(resource.getName());
    node.setCode(resource.getCode());
    node.setPath(resource.getPath());
    node.setIcon(resource.getIcon());
    node.setSortOrder(resource.getSortOrder());
    node.setVisible(resource.getVisible() == 1);
    node.setStatus(resource.getStatus());
    node.setApiPaths(apiPaths);
    node.setCreatedAt(resource.getCreatedAt());
    node.setUpdatedAt(resource.getUpdatedAt());
    return node;
  }
}
