package com.oa.service.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.ResourceCreateDTO;
import com.oa.common.model.system.dto.ResourceUpdateDTO;
import com.oa.common.model.system.enums.ResourceType;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.dao.system.ResourceApiMapper;
import com.oa.dao.system.RoleResourceMapper;
import com.oa.dao.system.SystemApiMapper;
import com.oa.dao.system.SystemResourceMapper;
import com.oa.entity.system.ResourceApiEntity;
import com.oa.entity.system.SystemResourceEntity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 系统资源管理服务测试。 */
@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {
  @Mock private SystemResourceMapper resourceMapper;
  @Mock private RoleResourceMapper roleResourceMapper;
  @Mock private ResourceApiMapper resourceApiMapper;
  @Mock private SystemApiMapper systemApiMapper;
  @Mock private PermissionService permissionService;

  private ResourceService resourceService;

  @BeforeEach
  void setUp() {
    resourceService =
        new ResourceService(
            resourceMapper,
            roleResourceMapper,
            resourceApiMapper,
            systemApiMapper,
            permissionService);
  }

  /** 资源新增和修改必须使用读已提交，使锁后二次资源图查询看到最新提交。 */
  @Test
  void shouldUseReadCommittedForGraphReloadTransactions() throws Exception {
    Transactional create =
        ResourceService.class
            .getMethod("create", ResourceCreateDTO.class)
            .getAnnotation(Transactional.class);
    Transactional update =
        ResourceService.class
            .getMethod("update", ResourceUpdateDTO.class)
            .getAnnotation(Transactional.class);

    assertThat(create).isNotNull();
    assertThat(update).isNotNull();
    assertThat(create.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    assertThat(update.isolation()).isEqualTo(Isolation.READ_COMMITTED);
  }

  /** 资源树应一次加载并按父子关系组装。 */
  @Test
  void shouldBuildTreeFromSingleLoad() {
    SystemResourceEntity root = resource(1, 0, ResourceType.DIRECTORY, SystemStatus.ENABLED);
    SystemResourceEntity menu = resource(2, 1, ResourceType.MENU, SystemStatus.ENABLED);
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(root, menu));

    var tree = resourceService.tree();

    assertThat(tree).hasSize(1);
    assertThat(tree.get(0).getChildren()).extracting(item -> item.getId()).containsExactly(2L);
    verify(resourceMapper).selectAllOrdered();
  }

  /** 孤儿节点和环路必须拒绝，不能返回残缺资源树。 */
  @Test
  void shouldRejectInvalidTree() {
    when(resourceMapper.selectAllOrdered())
        .thenReturn(List.of(resource(1, 99, ResourceType.MENU, SystemStatus.ENABLED)))
        .thenReturn(
            List.of(
                resource(1, 2, ResourceType.MENU, SystemStatus.ENABLED),
                resource(2, 1, ResourceType.MENU, SystemStatus.ENABLED)));

    assertCode(resourceService::tree, ExceptionCode.RESOURCE_TREE_INVALID);
    assertCode(resourceService::tree, ExceptionCode.RESOURCE_TREE_INVALID);
  }

  /** 不同资源类型必须严格校验编码、路径和图标字段。 */
  @Test
  void shouldValidateTypeFields() {
    ResourceCreateDTO directory = request(ResourceType.DIRECTORY);
    directory.setCode("directory");
    assertCode(() -> resourceService.create(directory), ExceptionCode.RESOURCE_TYPE_INVALID);

    ResourceCreateDTO menu = request(ResourceType.MENU);
    menu.setPath(null);
    assertCode(() -> resourceService.create(menu), ExceptionCode.RESOURCE_TYPE_INVALID);

    ResourceCreateDTO action = request(ResourceType.ACTION);
    action.setPath("/wrong");
    assertCode(() -> resourceService.create(action), ExceptionCode.RESOURCE_TYPE_INVALID);
  }

  /** 操作资源必须挂在菜单下，不能创建为根资源。 */
  @Test
  void shouldRejectRootActionOnCreate() {
    ResourceCreateDTO action = request(ResourceType.ACTION);

    assertCode(() -> resourceService.create(action), ExceptionCode.RESOURCE_PARENT_UNAVAILABLE);

    verify(resourceMapper, never()).selectByIdForUpdate(any(Long.class));
    verify(resourceMapper, never()).selectByIdsForUpdate(any());
    verify(resourceMapper, never()).insert(any(SystemResourceEntity.class));
  }

  /** 目录不能承载操作资源，新增和移动都必须拒绝。 */
  @Test
  void shouldRejectActionUnderDirectoryOnCreateAndMove() {
    ResourceCreateDTO created = request(ResourceType.ACTION);
    created.setParentId(1L);
    SystemResourceEntity directory = resource(1, 0, ResourceType.DIRECTORY, SystemStatus.ENABLED);
    when(resourceMapper.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(directory));
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(directory));
    assertCode(() -> resourceService.create(created), ExceptionCode.RESOURCE_TYPE_INVALID);

    ResourceUpdateDTO moved = updateRequest(ResourceType.ACTION);
    moved.setId(3L);
    moved.setParentId(1L);
    SystemResourceEntity action = resource(3, 2, ResourceType.ACTION, SystemStatus.ENABLED);
    when(resourceMapper.selectById(3L)).thenReturn(action);
    when(resourceMapper.selectByIdsForUpdate(List.of(1L, 3L)))
        .thenReturn(List.of(directory, action));
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(directory, action));
    assertCode(() -> resourceService.update(moved), ExceptionCode.RESOURCE_TYPE_INVALID);
  }

  /** 非根资源新增应先锁父资源，根资源新增不加行锁，二者均不刷新权限。 */
  @Test
  void shouldLockOnlyNonRootParentWhenCreatingWithoutInvalidation() {
    ResourceCreateDTO child = request(ResourceType.MENU);
    child.setParentId(1L);
    SystemResourceEntity parent = resource(1, 0, ResourceType.DIRECTORY, SystemStatus.ENABLED);
    when(resourceMapper.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(parent));
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(parent));

    resourceService.create(child);

    InOrder order = inOrder(resourceMapper);
    order.verify(resourceMapper).selectByIdsForUpdate(List.of(1L));
    order.verify(resourceMapper).insert(any(SystemResourceEntity.class));
    verify(permissionService, never()).invalidateAll();

    org.mockito.Mockito.reset(resourceMapper);
    resourceService.create(request(ResourceType.MENU));
    verify(resourceMapper, never()).selectByIdForUpdate(any(Long.class));
    verify(resourceMapper, never()).selectByIdsForUpdate(any());
    verify(permissionService, never()).invalidateAll();
  }

  /** 新增和移动资源必须按ID升序锁定目标完整祖先链。 */
  @Test
  void shouldLockCompleteAncestorChainInIdOrder() {
    SystemResourceEntity root = resource(1, 0, ResourceType.DIRECTORY, SystemStatus.ENABLED);
    SystemResourceEntity parent = resource(2, 1, ResourceType.MENU, SystemStatus.ENABLED);
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(root, parent));
    when(resourceMapper.selectByIdsForUpdate(List.of(1L, 2L))).thenReturn(List.of(root, parent));
    ResourceCreateDTO created = request(ResourceType.MENU);
    created.setParentId(2L);

    resourceService.create(created);

    verify(resourceMapper).selectByIdsForUpdate(List.of(1L, 2L));

    org.mockito.Mockito.reset(resourceMapper, permissionService);
    SystemResourceEntity current = resource(4, 0, ResourceType.MENU, SystemStatus.ENABLED);
    SystemResourceEntity middle = resource(3, 2, ResourceType.MENU, SystemStatus.ENABLED);
    when(resourceMapper.selectById(4L)).thenReturn(current);
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(root, parent, middle, current));
    when(resourceMapper.selectByIdsForUpdate(List.of(1L, 2L, 3L, 4L)))
        .thenReturn(List.of(root, parent, middle, current));
    ResourceUpdateDTO moved = updateRequest(ResourceType.MENU);
    moved.setId(4L);
    moved.setParentId(3L);

    resourceService.update(moved);

    verify(resourceMapper).selectByIdsForUpdate(List.of(1L, 2L, 3L, 4L));
  }

  /** 新增锁定祖先后发现状态或父链接变化时应拒绝并发覆盖。 */
  @Test
  void shouldRejectChangedAncestorAfterCreateSnapshot() {
    SystemResourceEntity root = resource(1, 0, ResourceType.DIRECTORY, SystemStatus.ENABLED);
    SystemResourceEntity parent = resource(2, 1, ResourceType.MENU, SystemStatus.ENABLED);
    SystemResourceEntity changedRoot =
        resource(1, 0, ResourceType.DIRECTORY, SystemStatus.DISABLED);
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(root, parent));
    when(resourceMapper.selectByIdsForUpdate(List.of(1L, 2L)))
        .thenReturn(List.of(changedRoot, parent));
    ResourceCreateDTO request = request(ResourceType.MENU);
    request.setParentId(2L);

    assertCode(
        () -> resourceService.create(request), ExceptionCode.RESOURCE_CONCURRENT_MODIFICATION);

    verify(resourceMapper, never()).insert(any(SystemResourceEntity.class));
  }

  /** 禁用父资源下不能新增启用资源，操作资源不能作为父资源。 */
  @Test
  void shouldRejectUnavailableParent() {
    ResourceCreateDTO request = request(ResourceType.MENU);
    request.setParentId(1L);
    SystemResourceEntity disabledParent =
        resource(1, 0, ResourceType.DIRECTORY, SystemStatus.DISABLED);
    SystemResourceEntity actionParent = resource(1, 0, ResourceType.ACTION, SystemStatus.ENABLED);
    when(resourceMapper.selectByIdsForUpdate(List.of(1L)))
        .thenReturn(List.of(disabledParent))
        .thenReturn(List.of(actionParent));
    when(resourceMapper.selectAllOrdered())
        .thenReturn(List.of(disabledParent))
        .thenReturn(List.of(actionParent));

    assertCode(() -> resourceService.create(request), ExceptionCode.RESOURCE_PARENT_UNAVAILABLE);
    assertCode(() -> resourceService.create(request), ExceptionCode.RESOURCE_PARENT_UNAVAILABLE);
  }

  /** 修改纯展示字段时即使祖先已禁用也应允许保存。 */
  @Test
  void shouldAllowDisplayUpdateUnderDisabledAncestor() {
    ResourceUpdateDTO request = updateRequest(ResourceType.MENU);
    request.setParentId(2L);
    SystemResourceEntity current = resource(1, 2, ResourceType.MENU, SystemStatus.ENABLED);
    SystemResourceEntity parent = resource(2, 0, ResourceType.MENU, SystemStatus.DISABLED);
    when(resourceMapper.selectById(1L)).thenReturn(current);
    when(resourceMapper.selectByIdsForUpdate(List.of(1L, 2L))).thenReturn(List.of(current, parent));
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(current, parent));

    resourceService.update(request);

    verify(resourceMapper).updateById(current);
    verify(permissionService, never()).invalidateAll();
  }

  /** 纯展示修改期间资源语义字段发生并发变化时应冲突且不刷新权限。 */
  @Test
  void shouldRejectConcurrentSemanticChangeDuringDisplayUpdate() {
    ResourceUpdateDTO request = updateRequest(ResourceType.MENU);
    SystemResourceEntity snapshot = resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED);
    SystemResourceEntity changed = resource(1, 0, ResourceType.MENU, SystemStatus.DISABLED);
    when(resourceMapper.selectById(1L)).thenReturn(snapshot);
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(snapshot));
    when(resourceMapper.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(changed));

    assertCode(
        () -> resourceService.update(request), ExceptionCode.RESOURCE_CONCURRENT_MODIFICATION);

    verify(permissionService, never()).invalidateAll();
    verify(resourceMapper, never()).updateById(any(SystemResourceEntity.class));
  }

  /** 锁定后祖先父链接发生变化时应拒绝并发覆盖。 */
  @Test
  void shouldRejectChangedAncestorLinkAfterUpdateSnapshot() {
    ResourceUpdateDTO request = updateRequest(ResourceType.MENU);
    request.setId(4L);
    request.setParentId(2L);
    SystemResourceEntity root = resource(1, 0, ResourceType.DIRECTORY, SystemStatus.ENABLED);
    SystemResourceEntity parent = resource(2, 1, ResourceType.MENU, SystemStatus.ENABLED);
    SystemResourceEntity changedParent = resource(2, 0, ResourceType.MENU, SystemStatus.ENABLED);
    SystemResourceEntity current = resource(4, 0, ResourceType.MENU, SystemStatus.ENABLED);
    when(resourceMapper.selectById(4L)).thenReturn(current);
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(root, parent, current));
    when(resourceMapper.selectByIdsForUpdate(List.of(1L, 2L, 4L)))
        .thenReturn(List.of(root, changedParent, current));

    assertCode(
        () -> resourceService.update(request), ExceptionCode.RESOURCE_CONCURRENT_MODIFICATION);

    verify(resourceMapper, never()).updateById(any(SystemResourceEntity.class));
  }

  /** 父级、状态或类型变化时必须在获取行锁前刷新权限。 */
  @Test
  void shouldInvalidateBeforeLockForSemanticUpdate() {
    ResourceUpdateDTO request = updateRequest(ResourceType.DIRECTORY);
    SystemResourceEntity current = resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED);
    when(resourceMapper.selectById(1L)).thenReturn(current);
    when(resourceMapper.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(current));
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(current));

    resourceService.update(request);

    InOrder order = inOrder(permissionService, resourceMapper);
    order.verify(permissionService).invalidateAll();
    order.verify(resourceMapper).selectByIdsForUpdate(List.of(1L));
    order.verify(resourceMapper).updateById(current);
  }

  /** 已关联接口的菜单或操作资源不能直接改为目录。 */
  @Test
  void shouldRejectDirectoryTypeWhenApisExist() {
    ResourceUpdateDTO request = updateRequest(ResourceType.DIRECTORY);
    SystemResourceEntity current = resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED);
    when(resourceMapper.selectById(1L)).thenReturn(current);
    when(resourceMapper.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(current));
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(current));
    when(resourceApiMapper.countByResourceId(1L)).thenReturn(1L);

    assertCode(() -> resourceService.update(request), ExceptionCode.RESOURCE_DIRECTORY_HAS_APIS);

    verify(resourceMapper, never()).updateById(any(SystemResourceEntity.class));
    verify(resourceApiMapper, never()).deleteByResourceId(1L);
  }

  /** 操作资源不能通过修改父级移动为根资源。 */
  @Test
  void shouldRejectMovingActionToRoot() {
    ResourceUpdateDTO request = updateRequest(ResourceType.ACTION);
    SystemResourceEntity current = resource(1, 2, ResourceType.ACTION, SystemStatus.ENABLED);
    when(resourceMapper.selectById(1L)).thenReturn(current);
    when(resourceMapper.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(current));
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(current));

    assertCode(() -> resourceService.update(request), ExceptionCode.RESOURCE_PARENT_UNAVAILABLE);
  }

  /** 状态修改应在权限失效后锁定当前资源再更新。 */
  @Test
  void shouldInvalidateBeforeLockWhenChangingStatus() {
    SystemResourceEntity current = resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED);
    when(resourceMapper.selectById(1L)).thenReturn(current);
    when(resourceMapper.selectByIdForUpdate(1L)).thenReturn(current);

    resourceService.changeStatus(1L, SystemStatus.DISABLED);

    InOrder order = inOrder(permissionService, resourceMapper);
    order.verify(permissionService).invalidateAll();
    order.verify(resourceMapper).selectByIdForUpdate(1L);
    order.verify(resourceMapper).updateById(current);
  }

  /** 状态修改锁定后发现快照已变化时应拒绝覆盖。 */
  @Test
  void shouldRejectConcurrentStatusChange() {
    SystemResourceEntity snapshot = resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED);
    SystemResourceEntity changed = resource(1, 0, ResourceType.MENU, SystemStatus.DISABLED);
    when(resourceMapper.selectById(1L)).thenReturn(snapshot);
    when(resourceMapper.selectByIdForUpdate(1L)).thenReturn(changed);

    assertCode(
        () -> resourceService.changeStatus(1L, SystemStatus.DISABLED),
        ExceptionCode.RESOURCE_CONCURRENT_MODIFICATION);

    verify(resourceMapper, never()).updateById(any(SystemResourceEntity.class));
  }

  /** 移动资源或从禁用改为启用时必须校验全部祖先已启用。 */
  @Test
  void shouldValidateAncestorsWhenMovingOrEnabling() {
    ResourceUpdateDTO moved = updateRequest(ResourceType.MENU);
    moved.setParentId(2L);
    SystemResourceEntity current = resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED);
    SystemResourceEntity disabledParent = resource(2, 0, ResourceType.MENU, SystemStatus.DISABLED);
    when(resourceMapper.selectById(1L)).thenReturn(current);
    when(resourceMapper.selectByIdsForUpdate(List.of(1L, 2L)))
        .thenReturn(List.of(current, disabledParent));
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(current, disabledParent));
    assertCode(() -> resourceService.update(moved), ExceptionCode.RESOURCE_PARENT_UNAVAILABLE);

    ResourceUpdateDTO enabled = updateRequest(ResourceType.MENU);
    enabled.setParentId(2L);
    current.setParentId(2L);
    current.setStatus(SystemStatus.DISABLED.getCode());
    assertCode(() -> resourceService.update(enabled), ExceptionCode.RESOURCE_PARENT_UNAVAILABLE);
  }

  /** 类型变更后无法承载现有子节点时必须拒绝，包括改成操作资源。 */
  @Test
  void shouldRejectTypeChangeIncompatibleWithChildren() {
    ResourceUpdateDTO directoryRequest = updateRequest(ResourceType.DIRECTORY);
    SystemResourceEntity current = resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED);
    when(resourceMapper.selectById(1L)).thenReturn(current);
    when(resourceMapper.selectByIdsForUpdate(List.of(1L))).thenReturn(List.of(current));
    when(resourceMapper.selectAllOrdered())
        .thenReturn(List.of(current, resource(2, 1, ResourceType.ACTION, SystemStatus.ENABLED)));
    assertCode(() -> resourceService.update(directoryRequest), ExceptionCode.RESOURCE_TYPE_INVALID);

    current.setParentId(3L);
    ResourceUpdateDTO actionRequest = updateRequest(ResourceType.ACTION);
    actionRequest.setParentId(3L);
    SystemResourceEntity parent = resource(3, 0, ResourceType.MENU, SystemStatus.ENABLED);
    when(resourceMapper.selectByIdsForUpdate(List.of(1L, 3L))).thenReturn(List.of(current, parent));
    when(resourceMapper.selectAllOrdered())
        .thenReturn(
            List.of(current, resource(2, 1, ResourceType.ACTION, SystemStatus.ENABLED), parent));
    assertCode(() -> resourceService.update(actionRequest), ExceptionCode.RESOURCE_TYPE_INVALID);
  }

  /** 锁前没有子节点但锁后出现子节点时，改为操作资源仍必须拒绝。 */
  @Test
  void shouldValidateTypeAgainstGraphReloadedAfterLock() {
    ResourceUpdateDTO request = updateRequest(ResourceType.ACTION);
    request.setId(4L);
    request.setParentId(3L);
    SystemResourceEntity parent = resource(3, 0, ResourceType.MENU, SystemStatus.ENABLED);
    SystemResourceEntity current = resource(4, 3, ResourceType.MENU, SystemStatus.ENABLED);
    SystemResourceEntity concurrentChild =
        resource(5, 4, ResourceType.ACTION, SystemStatus.ENABLED);
    when(resourceMapper.selectById(4L)).thenReturn(current);
    when(resourceMapper.selectAllOrdered())
        .thenReturn(List.of(parent, current))
        .thenReturn(List.of(parent, current, concurrentChild));
    when(resourceMapper.selectByIdsForUpdate(List.of(3L, 4L))).thenReturn(List.of(parent, current));

    assertCode(() -> resourceService.update(request), ExceptionCode.RESOURCE_TYPE_INVALID);

    verify(resourceMapper, org.mockito.Mockito.times(2)).selectAllOrdered();
    verify(resourceMapper, never()).updateById(any(SystemResourceEntity.class));
  }

  /** 新增节点后64层允许，65层必须拒绝。 */
  @Test
  void shouldEnforceCreateDepthBoundary() {
    ResourceCreateDTO request = request(ResourceType.MENU);
    request.setParentId(63L);
    when(resourceMapper.selectByIdsForUpdate(
            java.util.stream.LongStream.rangeClosed(1, 63).boxed().toList()))
        .thenReturn(chain(63));
    when(resourceMapper.selectByIdsForUpdate(
            java.util.stream.LongStream.rangeClosed(1, 64).boxed().toList()))
        .thenReturn(chain(64));
    when(resourceMapper.selectAllOrdered()).thenReturn(chain(63)).thenReturn(chain(64));

    resourceService.create(request);

    request.setParentId(64L);
    ResourceCreateDTO tooDeep = request;
    assertCode(() -> resourceService.create(tooDeep), ExceptionCode.RESOURCE_DEPTH_EXCEEDED);
  }

  /** 移动包含子节点的资源时必须按整个子树深度校验。 */
  @Test
  void shouldIncludeSubtreeDepthWhenMoving() {
    ResourceUpdateDTO request = updateRequest(ResourceType.MENU);
    request.setId(100L);
    request.setParentId(63L);
    SystemResourceEntity current = resource(100, 0, ResourceType.MENU, SystemStatus.ENABLED);
    List<SystemResourceEntity> resources = new ArrayList<>(chain(63));
    resources.add(current);
    resources.add(resource(101, 100, ResourceType.ACTION, SystemStatus.ENABLED));
    when(resourceMapper.selectById(100L)).thenReturn(current);
    List<Long> lockIds = new ArrayList<>();
    for (long id = 1; id <= 63; id++) {
      lockIds.add(id);
    }
    lockIds.add(100L);
    List<SystemResourceEntity> lockedResources = new ArrayList<>(chain(63));
    lockedResources.add(current);
    when(resourceMapper.selectByIdsForUpdate(lockIds)).thenReturn(lockedResources);
    when(resourceMapper.selectAllOrdered()).thenReturn(resources);

    assertCode(() -> resourceService.update(request), ExceptionCode.RESOURCE_DEPTH_EXCEEDED);
  }

  /** 锁后重新加载的子树变深导致65层时必须拒绝移动。 */
  @Test
  void shouldValidateDepthAgainstGraphReloadedAfterLock() {
    ResourceUpdateDTO request = updateRequest(ResourceType.MENU);
    request.setId(100L);
    request.setParentId(62L);
    SystemResourceEntity current = resource(100, 0, ResourceType.MENU, SystemStatus.ENABLED);
    SystemResourceEntity child = resource(101, 100, ResourceType.MENU, SystemStatus.ENABLED);
    SystemResourceEntity concurrentGrandchild =
        resource(102, 101, ResourceType.ACTION, SystemStatus.ENABLED);
    List<SystemResourceEntity> snapshotGraph = new ArrayList<>(chain(62));
    snapshotGraph.add(current);
    snapshotGraph.add(child);
    List<SystemResourceEntity> lockedGraph = new ArrayList<>(snapshotGraph);
    lockedGraph.add(concurrentGrandchild);
    List<Long> lockIds = new ArrayList<>();
    for (long id = 1; id <= 62; id++) {
      lockIds.add(id);
    }
    lockIds.add(100L);
    List<SystemResourceEntity> lockedResources = new ArrayList<>(chain(62));
    lockedResources.add(current);
    when(resourceMapper.selectById(100L)).thenReturn(current);
    when(resourceMapper.selectAllOrdered()).thenReturn(snapshotGraph).thenReturn(lockedGraph);
    when(resourceMapper.selectByIdsForUpdate(lockIds)).thenReturn(lockedResources);

    assertCode(() -> resourceService.update(request), ExceptionCode.RESOURCE_DEPTH_EXCEEDED);

    verify(resourceMapper, org.mockito.Mockito.times(2)).selectAllOrdered();
    verify(resourceMapper, never()).updateById(any(SystemResourceEntity.class));
  }

  /** 移动资源到自己的后代节点必须拒绝。 */
  @Test
  void shouldRejectMovingUnderDescendant() {
    ResourceUpdateDTO request = updateRequest(ResourceType.MENU);
    request.setParentId(2L);
    SystemResourceEntity current = resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED);
    SystemResourceEntity child = resource(2, 1, ResourceType.MENU, SystemStatus.ENABLED);
    when(resourceMapper.selectById(1L)).thenReturn(current);
    when(resourceMapper.selectByIdsForUpdate(List.of(1L, 2L))).thenReturn(List.of(current, child));
    when(resourceMapper.selectAllOrdered()).thenReturn(List.of(current, child));

    assertCode(() -> resourceService.update(request), ExceptionCode.RESOURCE_CYCLE);
  }

  /** 唯一索引并发冲突必须转换为明确数字业务异常。 */
  @Test
  void shouldTranslateConcurrentDuplicateKeys() {
    ResourceCreateDTO request = request(ResourceType.MENU);
    when(resourceMapper.insert(any(SystemResourceEntity.class)))
        .thenThrow(new DuplicateKeyException("udx_system_resource_code"))
        .thenThrow(new DuplicateKeyException("udx_system_resource_path"));

    assertCode(() -> resourceService.create(request), ExceptionCode.RESOURCE_CODE_DUPLICATED);
    assertCode(() -> resourceService.create(request), ExceptionCode.RESOURCE_PATH_DUPLICATED);
  }

  /** 删除资源应先标记权限失效，再锁资源并依次校验关联。 */
  @Test
  void shouldLockAndValidateBeforeDelete() {
    when(resourceMapper.selectByIdForUpdate(1L))
        .thenReturn(resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED));

    resourceService.delete(1L);

    InOrder order =
        inOrder(permissionService, resourceMapper, roleResourceMapper, resourceApiMapper);
    order.verify(permissionService).invalidateAll();
    order.verify(resourceMapper).selectByIdForUpdate(1L);
    order.verify(resourceMapper).countByParentId(1L);
    order.verify(roleResourceMapper).countByResourceId(1L);
    order.verify(resourceApiMapper).countByResourceId(1L);
    order.verify(resourceMapper).deleteById(1L);
  }

  /** 存在子节点、角色关联或接口关联时均不得删除资源。 */
  @Test
  void shouldRejectDeleteConstraints() {
    when(resourceMapper.selectByIdForUpdate(1L))
        .thenReturn(resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED));
    when(resourceMapper.countByParentId(1L)).thenReturn(1L);
    assertCode(() -> resourceService.delete(1L), ExceptionCode.RESOURCE_HAS_CHILDREN);

    org.mockito.Mockito.reset(resourceMapper, roleResourceMapper, resourceApiMapper);
    when(resourceMapper.selectByIdForUpdate(1L))
        .thenReturn(resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED));
    when(roleResourceMapper.countByResourceId(1L)).thenReturn(1L);
    assertCode(() -> resourceService.delete(1L), ExceptionCode.RESOURCE_HAS_ROLES);

    org.mockito.Mockito.reset(resourceMapper, roleResourceMapper, resourceApiMapper);
    when(resourceMapper.selectByIdForUpdate(1L))
        .thenReturn(resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED));
    when(resourceApiMapper.countByResourceId(1L)).thenReturn(1L);
    assertCode(() -> resourceService.delete(1L), ExceptionCode.RESOURCE_HAS_APIS);
  }

  /** 保存接口应去重、全量校验并按每批最多500条写入。 */
  @Test
  void shouldValidateDeduplicateAndBatchApis() {
    when(resourceMapper.selectByIdForUpdate(1L))
        .thenReturn(resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED));
    List<Long> apiIds = new ArrayList<>();
    for (long id = 1; id <= 501; id++) {
      apiIds.add(id);
    }
    apiIds.add(1L);
    when(systemApiMapper.selectEnabledIds(apiIds.subList(0, 500)))
        .thenReturn(apiIds.subList(0, 500));
    when(systemApiMapper.selectEnabledIds(apiIds.subList(500, 501)))
        .thenReturn(apiIds.subList(500, 501));

    resourceService.saveApis(1L, apiIds);

    ArgumentCaptor<List<ResourceApiEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(resourceApiMapper, org.mockito.Mockito.times(2)).insertBatch(captor.capture());
    assertThat(captor.getAllValues()).extracting(List::size).containsExactly(500, 1);
    InOrder order = inOrder(permissionService, resourceMapper, systemApiMapper, resourceApiMapper);
    order.verify(permissionService).invalidateAll();
    order.verify(resourceMapper).selectByIdForUpdate(1L);
    order.verify(systemApiMapper).selectEnabledIds(apiIds.subList(0, 500));
    order.verify(systemApiMapper).selectEnabledIds(apiIds.subList(500, 501));
    order.verify(resourceApiMapper).deleteByResourceId(1L);
  }

  /** 资源接口回显应先确认资源存在并按Mapper顺序返回。 */
  @Test
  void shouldReturnOrderedApiIds() {
    when(resourceMapper.selectById(1L))
        .thenReturn(resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED));
    when(resourceApiMapper.selectApiIdsByResourceId(1L)).thenReturn(List.of(2L, 3L));

    assertThat(resourceService.apiIds(1L)).containsExactly(2L, 3L);
  }

  /** 请求和现有接口都为空时应直接返回且不刷新权限或写关联。 */
  @Test
  void shouldClearApisWithoutEmptyCollectionQuery() {
    when(resourceMapper.selectByIdForUpdate(1L))
        .thenReturn(resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED));

    resourceService.saveApis(1L, List.of());

    verify(systemApiMapper, never()).selectEnabledIds(any());
    verify(permissionService, never()).invalidateAll();
    verify(resourceApiMapper, never()).deleteByResourceId(1L);
    verify(resourceApiMapper, never()).insertBatch(any());
  }

  /** 现有关联非空而请求为空时应刷新权限并清空关联。 */
  @Test
  void shouldClearExistingApis() {
    when(resourceApiMapper.selectApiIdsByResourceId(1L))
        .thenReturn(List.of(2L))
        .thenReturn(List.of(2L));
    when(resourceMapper.selectByIdForUpdate(1L))
        .thenReturn(resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED));

    resourceService.saveApis(1L, List.of());

    verify(permissionService).invalidateAll();
    verify(resourceApiMapper).deleteByResourceId(1L);
    verify(resourceApiMapper, never()).insertBatch(any());
  }

  /** 相同接口关联应精确返回，并发变化时应冲突且不刷新权限。 */
  @Test
  void shouldNoOpSameApisAndRejectConcurrentChange() {
    when(resourceApiMapper.selectApiIdsByResourceId(1L))
        .thenReturn(List.of(2L))
        .thenReturn(List.of(2L));
    when(resourceMapper.selectByIdForUpdate(1L))
        .thenReturn(resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED));

    resourceService.saveApis(1L, List.of(2L, 2L));

    verify(permissionService, never()).invalidateAll();
    verify(resourceApiMapper, never()).deleteByResourceId(1L);

    org.mockito.Mockito.reset(resourceApiMapper, resourceMapper, permissionService);
    when(resourceApiMapper.selectApiIdsByResourceId(1L))
        .thenReturn(List.of(2L))
        .thenReturn(List.of(3L));
    when(resourceMapper.selectByIdForUpdate(1L))
        .thenReturn(resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED));

    assertCode(
        () -> resourceService.saveApis(1L, List.of(2L)),
        ExceptionCode.RESOURCE_CONCURRENT_MODIFICATION);
    verify(permissionService, never()).invalidateAll();
    verify(resourceApiMapper, never()).deleteByResourceId(1L);
  }

  /** 目录和禁用资源都不能保存接口，接口必须全部存在且启用。 */
  @Test
  void shouldRejectInvalidApiAssociation() {
    when(resourceMapper.selectByIdForUpdate(1L))
        .thenReturn(resource(1, 0, ResourceType.DIRECTORY, SystemStatus.ENABLED))
        .thenReturn(resource(1, 0, ResourceType.MENU, SystemStatus.DISABLED))
        .thenReturn(resource(1, 0, ResourceType.MENU, SystemStatus.ENABLED));
    assertCode(
        () -> resourceService.saveApis(1L, List.of()), ExceptionCode.RESOURCE_API_TYPE_INVALID);
    assertCode(() -> resourceService.saveApis(1L, List.of()), ExceptionCode.RESOURCE_UNAVAILABLE);
    when(systemApiMapper.selectEnabledIds(List.of(2L))).thenReturn(List.of());
    assertCode(
        () -> resourceService.saveApis(1L, List.of(2L)), ExceptionCode.RESOURCE_API_UNAVAILABLE);
    verify(resourceApiMapper, never()).deleteByResourceId(1L);
  }

  private ResourceCreateDTO request(ResourceType type) {
    ResourceCreateDTO request = new ResourceCreateDTO();
    request.setParentId(0L);
    request.setType(type);
    request.setName("资源");
    request.setCode(type == ResourceType.DIRECTORY ? null : "resource");
    request.setPath(type == ResourceType.MENU ? "/resource" : null);
    request.setStatus(SystemStatus.ENABLED);
    request.setVisible(true);
    return request;
  }

  private ResourceUpdateDTO updateRequest(ResourceType type) {
    ResourceUpdateDTO request = new ResourceUpdateDTO();
    request.setId(1L);
    request.setParentId(0L);
    request.setType(type);
    request.setName("资源");
    request.setCode(type == ResourceType.DIRECTORY ? null : "resource");
    request.setPath(type == ResourceType.MENU ? "/resource" : null);
    request.setStatus(SystemStatus.ENABLED);
    return request;
  }

  private SystemResourceEntity resource(
      long id, long parentId, ResourceType type, SystemStatus status) {
    SystemResourceEntity resource = new SystemResourceEntity();
    resource.setId(id);
    resource.setParentId(parentId);
    resource.setType(type.getCode());
    resource.setName("资源" + id);
    resource.setCode(type == ResourceType.DIRECTORY ? null : "resource-" + id);
    resource.setPath(type == ResourceType.MENU ? "/resource-" + id : null);
    resource.setStatus(status.getCode());
    return resource;
  }

  private List<SystemResourceEntity> chain(int depth) {
    List<SystemResourceEntity> resources = new ArrayList<>();
    for (int id = 1; id <= depth; id++) {
      resources.add(resource(id, id - 1L, ResourceType.MENU, SystemStatus.ENABLED));
    }
    return resources;
  }

  private void assertCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, ExceptionCode code) {
    assertThatThrownBy(callable)
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(code);
  }
}
