package com.oa.service.system;

import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.ResourceCreateDTO;
import com.oa.common.model.system.dto.ResourceUpdateDTO;
import com.oa.common.model.system.enums.ResourceType;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.model.system.vo.ResourceVO;
import com.oa.dao.system.ResourceApiMapper;
import com.oa.dao.system.RoleResourceMapper;
import com.oa.dao.system.SystemApiMapper;
import com.oa.dao.system.SystemResourceMapper;
import com.oa.entity.system.ResourceApiEntity;
import com.oa.entity.system.SystemResourceEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 系统资源及资源接口关联管理。 */
@Service
public class ResourceService {
  private static final int MAX_DEPTH = 64;
  private static final int RELATION_BATCH_SIZE = 500;
  private static final String CODE_INDEX = "udx_system_resource_code";
  private static final String PATH_INDEX = "udx_system_resource_path";

  private final SystemResourceMapper resourceMapper;
  private final RoleResourceMapper roleResourceMapper;
  private final ResourceApiMapper resourceApiMapper;
  private final SystemApiMapper systemApiMapper;
  private final PermissionService permissionService;

  public ResourceService(
      SystemResourceMapper resourceMapper,
      RoleResourceMapper roleResourceMapper,
      ResourceApiMapper resourceApiMapper,
      SystemApiMapper systemApiMapper,
      PermissionService permissionService) {
    this.resourceMapper = resourceMapper;
    this.roleResourceMapper = roleResourceMapper;
    this.resourceApiMapper = resourceApiMapper;
    this.systemApiMapper = systemApiMapper;
    this.permissionService = permissionService;
  }

  /** 查询完整资源树。 */
  public List<ResourceVO> tree() {
    List<SystemResourceEntity> resources = resourceMapper.selectAllOrdered();
    Map<Long, ResourceVO> nodes = new HashMap<>();
    Map<Long, SystemResourceEntity> entities = new HashMap<>();
    for (SystemResourceEntity resource : resources) {
      entities.put(resource.getId(), resource);
      nodes.put(resource.getId(), toVO(resource));
    }
    validateTree(entities);
    List<ResourceVO> roots = new ArrayList<>();
    for (SystemResourceEntity resource : resources) {
      ResourceVO node = nodes.get(resource.getId());
      if (resource.getParentId() == 0) {
        roots.add(node);
      } else {
        nodes.get(resource.getParentId()).getChildren().add(node);
      }
    }
    return roots;
  }

  /** 查询资源详情。 */
  public ResourceVO detail(long id) {
    return toVO(required(id));
  }

  /** 查询资源关联的接口ID。 */
  public List<Long> apiIds(long resourceId) {
    required(resourceId);
    return resourceApiMapper.selectApiIdsByResourceId(resourceId);
  }

  /** 新增资源。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ResourceVO create(ResourceCreateDTO request) {
    validateTypeFields(request);
    String code = nullableText(request.getCode());
    String path = nullableText(request.getPath());
    String icon = nullableText(request.getIcon());
    List<SystemResourceEntity> snapshotGraph = resourceMapper.selectAllOrdered();
    List<SystemResourceEntity> ancestors =
        ancestorChain(request.getParentId(), index(snapshotGraph));
    if (!ancestors.isEmpty()) {
      List<Long> lockIds = ancestors.stream().map(SystemResourceEntity::getId).sorted().toList();
      Map<Long, SystemResourceEntity> locked = index(resourceMapper.selectByIdsForUpdate(lockIds));
      verifyLockedAncestors(ancestors, locked);
    }
    validateParentAndDepth(
        0,
        request.getParentId(),
        request.getType(),
        request.getStatus() == SystemStatus.ENABLED,
        snapshotGraph);
    validateUnique(code, path, null);
    LocalDateTime now = LocalDateTime.now();
    SystemResourceEntity resource = new SystemResourceEntity();
    copyFields(request, code, path, icon, resource);
    resource.setCreatedAt(now);
    resource.setUpdatedAt(now);
    try {
      resourceMapper.insert(resource);
    } catch (DuplicateKeyException exception) {
      throw translateDuplicateKey(exception);
    }
    return toVO(resource);
  }

  /** 修改资源。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ResourceVO update(ResourceUpdateDTO request) {
    SystemResourceEntity snapshot = required(request.getId());
    validateTypeFields(request);
    String code = nullableText(request.getCode());
    String path = nullableText(request.getPath());
    String icon = nullableText(request.getIcon());
    List<SystemResourceEntity> snapshotGraph = resourceMapper.selectAllOrdered();
    List<SystemResourceEntity> ancestors =
        ancestorChain(request.getParentId(), index(snapshotGraph));
    boolean moved = snapshot.getParentId() != request.getParentId();
    boolean statusChanged = snapshot.getStatus() != request.getStatus().getCode();
    boolean typeChanged = !snapshot.getType().equals(request.getType().getCode());
    if (moved || statusChanged || typeChanged) {
      permissionService.invalidateAll();
    }
    List<Long> lockIds =
        java.util.stream.Stream.concat(
                java.util.stream.Stream.of(request.getId()),
                ancestors.stream().map(SystemResourceEntity::getId))
            .distinct()
            .sorted()
            .toList();
    Map<Long, SystemResourceEntity> locked = index(resourceMapper.selectByIdsForUpdate(lockIds));
    SystemResourceEntity resource = locked.get(request.getId());
    if (resource == null) {
      throw new BusinessException(ExceptionCode.RESOURCE_NOT_FOUND);
    }
    verifyUnchanged(snapshot, resource);
    verifyLockedAncestors(ancestors, locked);
    List<SystemResourceEntity> lockedGraph = resourceMapper.selectAllOrdered();
    moved = resource.getParentId() != request.getParentId();
    boolean enabling =
        resource.getStatus() != SystemStatus.ENABLED.getCode()
            && request.getStatus() == SystemStatus.ENABLED;
    validateParentAndDepth(
        resource.getId(), request.getParentId(), request.getType(), moved || enabling, lockedGraph);
    validateChildrenForType(resource.getId(), request.getType(), lockedGraph);
    if (request.getType() == ResourceType.DIRECTORY
        && !ResourceType.DIRECTORY.getCode().equals(resource.getType())
        && resourceApiMapper.countByResourceId(resource.getId()) > 0) {
      throw new BusinessException(ExceptionCode.RESOURCE_DIRECTORY_HAS_APIS);
    }
    validateUnique(code, path, resource.getId());
    copyFields(request, code, path, icon, resource);
    resource.setUpdatedAt(LocalDateTime.now());
    try {
      resourceMapper.updateById(resource);
    } catch (DuplicateKeyException exception) {
      throw translateDuplicateKey(exception);
    }
    return toVO(resource);
  }

  /** 修改资源状态。 */
  @Transactional
  public void changeStatus(long id, SystemStatus status) {
    SystemResourceEntity snapshot = required(id);
    boolean changed = snapshot.getStatus() != status.getCode();
    if (changed) {
      permissionService.invalidateAll();
    }
    SystemResourceEntity resource = resourceMapper.selectByIdForUpdate(id);
    if (resource == null) {
      throw new BusinessException(ExceptionCode.RESOURCE_NOT_FOUND);
    }
    if (resource.getStatus() != snapshot.getStatus()) {
      throw concurrentModification();
    }
    if (!changed) {
      return;
    }
    if (status == SystemStatus.ENABLED) {
      validateEnabledAncestors(resource, resourceMapper.selectAllOrdered());
    }
    resource.setStatus(status.getCode());
    resource.setUpdatedAt(LocalDateTime.now());
    resourceMapper.updateById(resource);
  }

  /** 删除没有子节点及关联关系的资源。 */
  @Transactional
  public void delete(long id) {
    permissionService.invalidateAll();
    if (resourceMapper.selectByIdForUpdate(id) == null) {
      throw new BusinessException(ExceptionCode.RESOURCE_NOT_FOUND);
    }
    if (resourceMapper.countByParentId(id) > 0) {
      throw new BusinessException(ExceptionCode.RESOURCE_HAS_CHILDREN);
    }
    if (roleResourceMapper.countByResourceId(id) > 0) {
      throw new BusinessException(ExceptionCode.RESOURCE_HAS_ROLES);
    }
    if (resourceApiMapper.countByResourceId(id) > 0) {
      throw new BusinessException(ExceptionCode.RESOURCE_HAS_APIS);
    }
    resourceMapper.deleteById(id);
  }

  /** 全量保存资源接口关联，空列表表示清空。 */
  @Transactional
  public void saveApis(long resourceId, List<Long> apiIds) {
    List<Long> snapshotApiIds = resourceApiMapper.selectApiIdsByResourceId(resourceId);
    List<Long> distinctIds = new LinkedHashSet<>(apiIds).stream().toList();
    boolean changed = !new HashSet<>(snapshotApiIds).equals(new HashSet<>(distinctIds));
    if (changed) {
      permissionService.invalidateAll();
    }
    SystemResourceEntity resource = resourceMapper.selectByIdForUpdate(resourceId);
    if (resource == null) {
      throw new BusinessException(ExceptionCode.RESOURCE_NOT_FOUND);
    }
    if (ResourceType.DIRECTORY.getCode().equals(resource.getType())) {
      throw new BusinessException(ExceptionCode.RESOURCE_API_TYPE_INVALID);
    }
    if (resource.getStatus() != SystemStatus.ENABLED.getCode()) {
      throw new BusinessException(ExceptionCode.RESOURCE_UNAVAILABLE);
    }
    List<Long> lockedApiIds = resourceApiMapper.selectApiIdsByResourceId(resourceId);
    if (!changed && !new HashSet<>(lockedApiIds).equals(new HashSet<>(snapshotApiIds))) {
      throw concurrentModification();
    }
    if (!changed) {
      return;
    }
    Set<Long> enabledIds = new HashSet<>();
    for (int start = 0; start < distinctIds.size(); start += RELATION_BATCH_SIZE) {
      int end = Math.min(start + RELATION_BATCH_SIZE, distinctIds.size());
      enabledIds.addAll(systemApiMapper.selectEnabledIds(distinctIds.subList(start, end)));
    }
    if (!enabledIds.equals(new HashSet<>(distinctIds))) {
      throw new BusinessException(ExceptionCode.RESOURCE_API_UNAVAILABLE);
    }
    resourceApiMapper.deleteByResourceId(resourceId);
    insertApiRelations(resourceId, distinctIds);
  }

  private void validateTree(Map<Long, SystemResourceEntity> resources) {
    for (SystemResourceEntity resource : resources.values()) {
      Set<Long> visited = new HashSet<>();
      SystemResourceEntity current = resource;
      for (int depth = 1; ; depth++) {
        if (depth > MAX_DEPTH) {
          throw new BusinessException(ExceptionCode.RESOURCE_DEPTH_EXCEEDED);
        }
        if (!visited.add(current.getId())) {
          throw new BusinessException(ExceptionCode.RESOURCE_TREE_INVALID);
        }
        if (current.getParentId() == 0) {
          break;
        }
        current = resources.get(current.getParentId());
        if (current == null) {
          throw new BusinessException(ExceptionCode.RESOURCE_TREE_INVALID);
        }
      }
    }
  }

  private List<SystemResourceEntity> ancestorChain(
      long parentId, Map<Long, SystemResourceEntity> resources) {
    List<SystemResourceEntity> result = new ArrayList<>();
    Set<Long> visited = new HashSet<>();
    long currentId = parentId;
    while (currentId != 0) {
      if (result.size() >= MAX_DEPTH || !visited.add(currentId)) {
        throw new BusinessException(ExceptionCode.RESOURCE_CYCLE);
      }
      SystemResourceEntity current = resources.get(currentId);
      if (current == null) {
        throw new BusinessException(ExceptionCode.RESOURCE_PARENT_UNAVAILABLE);
      }
      result.add(current);
      currentId = current.getParentId();
    }
    return result;
  }

  private void verifyLockedAncestors(
      List<SystemResourceEntity> snapshots, Map<Long, SystemResourceEntity> locked) {
    for (SystemResourceEntity snapshot : snapshots) {
      SystemResourceEntity current = locked.get(snapshot.getId());
      if (current == null
          || current.getParentId() != snapshot.getParentId()
          || current.getStatus() != snapshot.getStatus()
          || !Objects.equals(current.getType(), snapshot.getType())) {
        throw concurrentModification();
      }
    }
  }

  private void verifyUnchanged(SystemResourceEntity snapshot, SystemResourceEntity lockedResource) {
    if (lockedResource.getParentId() != snapshot.getParentId()
        || lockedResource.getStatus() != snapshot.getStatus()
        || !Objects.equals(lockedResource.getType(), snapshot.getType())) {
      throw concurrentModification();
    }
  }

  private BusinessException concurrentModification() {
    return new BusinessException(ExceptionCode.RESOURCE_CONCURRENT_MODIFICATION);
  }

  private void validateParentAndDepth(
      long id,
      long parentId,
      ResourceType type,
      boolean requireEnabledAncestors,
      List<SystemResourceEntity> all) {
    Map<Long, SystemResourceEntity> resources = index(all);
    if (id != 0 && id == parentId) {
      throw new BusinessException(ExceptionCode.RESOURCE_CYCLE);
    }
    if (type == ResourceType.ACTION && parentId == 0) {
      throw new BusinessException(ExceptionCode.RESOURCE_PARENT_UNAVAILABLE);
    }
    int parentDepth = 0;
    if (parentId != 0) {
      SystemResourceEntity parent = resources.get(parentId);
      if (parent == null || ResourceType.ACTION.getCode().equals(parent.getType())) {
        throw new BusinessException(ExceptionCode.RESOURCE_PARENT_UNAVAILABLE);
      }
      if (!allows(ResourceType.valueOf(parent.getType()), type)) {
        throw new BusinessException(ExceptionCode.RESOURCE_TYPE_INVALID);
      }
      Set<Long> visited = new HashSet<>();
      for (SystemResourceEntity current = parent; current != null; ) {
        if (!visited.add(current.getId()) || current.getId() == id) {
          throw new BusinessException(ExceptionCode.RESOURCE_CYCLE);
        }
        parentDepth++;
        if (requireEnabledAncestors && current.getStatus() != SystemStatus.ENABLED.getCode()) {
          throw new BusinessException(ExceptionCode.RESOURCE_PARENT_UNAVAILABLE);
        }
        current = current.getParentId() == 0 ? null : resources.get(current.getParentId());
        if (current == null && parentDepth > 0 && ancestorMissing(parent, resources, parentDepth)) {
          throw new BusinessException(ExceptionCode.RESOURCE_PARENT_UNAVAILABLE);
        }
      }
    }
    int subtreeDepth = id == 0 ? 1 : subtreeDepth(id, all, new HashSet<>());
    if (parentDepth + subtreeDepth > MAX_DEPTH) {
      throw new BusinessException(ExceptionCode.RESOURCE_DEPTH_EXCEEDED);
    }
  }

  private boolean ancestorMissing(
      SystemResourceEntity start, Map<Long, SystemResourceEntity> resources, int ignoredDepth) {
    SystemResourceEntity current = start;
    while (current.getParentId() != 0) {
      current = resources.get(current.getParentId());
      if (current == null) {
        return true;
      }
    }
    return false;
  }

  private int subtreeDepth(long id, List<SystemResourceEntity> all, Set<Long> visiting) {
    if (!visiting.add(id)) {
      throw new BusinessException(ExceptionCode.RESOURCE_CYCLE);
    }
    int max = 1;
    for (SystemResourceEntity candidate : all) {
      if (candidate.getParentId() == id) {
        max = Math.max(max, 1 + subtreeDepth(candidate.getId(), all, visiting));
      }
    }
    visiting.remove(id);
    return max;
  }

  private void validateChildrenForType(
      long id, ResourceType type, List<SystemResourceEntity> resources) {
    for (SystemResourceEntity child : resources) {
      if (child.getParentId() == id && !allows(type, ResourceType.valueOf(child.getType()))) {
        throw new BusinessException(ExceptionCode.RESOURCE_TYPE_INVALID);
      }
    }
  }

  private boolean allows(ResourceType parent, ResourceType child) {
    return switch (parent) {
      case DIRECTORY -> child == ResourceType.DIRECTORY || child == ResourceType.MENU;
      case MENU -> child == ResourceType.MENU || child == ResourceType.ACTION;
      case ACTION -> false;
    };
  }

  private void validateEnabledAncestors(
      SystemResourceEntity resource, List<SystemResourceEntity> all) {
    Map<Long, SystemResourceEntity> resources = index(all);
    long parentId = resource.getParentId();
    for (int depth = 0; parentId != 0 && depth < MAX_DEPTH; depth++) {
      SystemResourceEntity parent = resources.get(parentId);
      if (parent == null || parent.getStatus() != SystemStatus.ENABLED.getCode()) {
        throw new BusinessException(ExceptionCode.RESOURCE_PARENT_UNAVAILABLE);
      }
      parentId = parent.getParentId();
    }
    if (parentId != 0) {
      throw new BusinessException(ExceptionCode.RESOURCE_DEPTH_EXCEEDED);
    }
  }

  private void validateTypeFields(ResourceCreateDTO request) {
    String code = nullableText(request.getCode());
    String path = nullableText(request.getPath());
    String icon = nullableText(request.getIcon());
    switch (request.getType()) {
      case DIRECTORY -> {
        if (code != null || path != null) {
          throw new BusinessException(ExceptionCode.RESOURCE_TYPE_INVALID);
        }
      }
      case MENU -> {
        if (code == null || path == null) {
          throw new BusinessException(ExceptionCode.RESOURCE_TYPE_INVALID);
        }
      }
      case ACTION -> {
        if (code == null || path != null || icon != null) {
          throw new BusinessException(ExceptionCode.RESOURCE_TYPE_INVALID);
        }
      }
    }
  }

  private void validateUnique(String code, String path, Long id) {
    rejectDuplicate(
        code == null ? null : resourceMapper.selectByCode(code),
        id,
        ExceptionCode.RESOURCE_CODE_DUPLICATED);
    rejectDuplicate(
        path == null ? null : resourceMapper.selectByPath(path),
        id,
        ExceptionCode.RESOURCE_PATH_DUPLICATED);
  }

  private void rejectDuplicate(
      SystemResourceEntity duplicate, Long id, ExceptionCode exceptionCode) {
    if (duplicate != null && !Objects.equals(duplicate.getId(), id)) {
      throw new BusinessException(exceptionCode);
    }
  }

  private SystemResourceEntity required(long id) {
    SystemResourceEntity resource = resourceMapper.selectById(id);
    if (resource == null) {
      throw new BusinessException(ExceptionCode.RESOURCE_NOT_FOUND);
    }
    return resource;
  }

  private Map<Long, SystemResourceEntity> index(List<SystemResourceEntity> resources) {
    Map<Long, SystemResourceEntity> result = new HashMap<>();
    for (SystemResourceEntity resource : resources) {
      result.put(resource.getId(), resource);
    }
    return result;
  }

  private void copyFields(
      ResourceCreateDTO request,
      String code,
      String path,
      String icon,
      SystemResourceEntity resource) {
    resource.setParentId(request.getParentId());
    resource.setType(request.getType().getCode());
    resource.setName(request.getName().strip());
    resource.setCode(code);
    resource.setPath(path);
    resource.setIcon(icon);
    resource.setSortOrder(request.getSortOrder());
    resource.setVisible(request.getVisible() ? 1 : 0);
    resource.setStatus(request.getStatus().getCode());
  }

  private void insertApiRelations(long resourceId, List<Long> apiIds) {
    LocalDateTime now = LocalDateTime.now();
    for (int start = 0; start < apiIds.size(); start += RELATION_BATCH_SIZE) {
      int end = Math.min(start + RELATION_BATCH_SIZE, apiIds.size());
      List<ResourceApiEntity> relations =
          apiIds.subList(start, end).stream().map(id -> relation(resourceId, id, now)).toList();
      resourceApiMapper.insertBatch(relations);
    }
  }

  private ResourceApiEntity relation(long resourceId, long apiId, LocalDateTime now) {
    ResourceApiEntity relation = new ResourceApiEntity();
    relation.setResourceId(resourceId);
    relation.setApiId(apiId);
    relation.setCreatedAt(now);
    return relation;
  }

  private RuntimeException translateDuplicateKey(DuplicateKeyException exception) {
    String message = exception.getMessage();
    if (message != null && message.contains(CODE_INDEX)) {
      return new BusinessException(ExceptionCode.RESOURCE_CODE_DUPLICATED);
    }
    if (message != null && message.contains(PATH_INDEX)) {
      return new BusinessException(ExceptionCode.RESOURCE_PATH_DUPLICATED);
    }
    return exception;
  }

  private ResourceVO toVO(SystemResourceEntity resource) {
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
    result.setStatus(
        resource.getStatus() == SystemStatus.ENABLED.getCode()
            ? SystemStatus.ENABLED
            : SystemStatus.DISABLED);
    result.setChildren(new ArrayList<>());
    result.setCreatedAt(resource.getCreatedAt());
    result.setUpdatedAt(resource.getUpdatedAt());
    return result;
  }

  private String nullableText(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
