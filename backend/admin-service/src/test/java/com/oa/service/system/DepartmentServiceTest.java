package com.oa.service.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.DepartmentCreateDTO;
import com.oa.common.model.system.dto.DepartmentUpdateDTO;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.dao.system.DepartmentMapper;
import com.oa.dao.system.EmployeeMapper;
import com.oa.entity.system.DepartmentEntity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 部门管理服务测试。 */
@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {
  @Mock private DepartmentMapper departmentMapper;
  @Mock private EmployeeMapper employeeMapper;
  private DepartmentService service;

  @BeforeEach
  void setUp() {
    service = new DepartmentService(departmentMapper, employeeMapper);
  }

  /** 部门树应按排序值和ID稳定排序。 */
  @Test
  void shouldBuildStableTree() {
    when(departmentMapper.selectAllOrdered())
        .thenReturn(List.of(department(3, 1, 1), department(2, 0, 2), department(1, 0, 2)));

    var tree = service.tree();

    assertThat(tree).extracting(item -> item.getId()).containsExactly(1L, 2L);
    assertThat(tree.get(0).getChildren()).extracting(item -> item.getId()).containsExactly(3L);
  }

  /** 孤儿和环路数据应失败，不得递归溢出。 */
  @Test
  void shouldRejectBrokenTree() {
    when(departmentMapper.selectAllOrdered())
        .thenReturn(List.of(department(1, 2, 0), department(2, 1, 0)));
    assertCode(() -> service.tree(), ExceptionCode.DEPARTMENT_CYCLE);

    when(departmentMapper.selectAllOrdered()).thenReturn(List.of(department(1, 9, 0)));
    assertCode(() -> service.tree(), ExceptionCode.DEPARTMENT_PARENT_UNAVAILABLE);
  }

  /** 超过64层的树应失败。 */
  @Test
  void shouldRejectMoreThanSixtyFourLevels() {
    List<DepartmentEntity> departments = new ArrayList<>();
    for (int id = 1; id <= 65; id++) {
      departments.add(department(id, id - 1, 0));
    }
    when(departmentMapper.selectAllOrdered()).thenReturn(departments);
    assertCode(() -> service.tree(), ExceptionCode.DEPARTMENT_DEPTH_EXCEEDED);
  }

  /** 新增部门要求父部门及全部祖先启用。 */
  @Test
  void shouldRejectDisabledAncestorOnCreate() {
    DepartmentEntity root = department(1, 0, 0);
    root.setStatus(SystemStatus.DISABLED.getCode());
    when(departmentMapper.selectAllOrdered()).thenReturn(List.of(root, department(2, 1, 0)));
    DepartmentCreateDTO request = createRequest(2);
    assertCode(() -> service.create(request), ExceptionCode.DEPARTMENT_PARENT_UNAVAILABLE);
    verify(departmentMapper, never()).insert(any(DepartmentEntity.class));
  }

  /** 启用部门时要求父部门及全部祖先启用，禁用部门不附加该限制。 */
  @Test
  void shouldValidateAncestorsOnlyWhenEnabling() {
    DepartmentEntity root = department(1, 0, 0);
    root.setStatus(SystemStatus.DISABLED.getCode());
    DepartmentEntity child = department(2, 1, 0);
    child.setStatus(SystemStatus.DISABLED.getCode());
    when(departmentMapper.selectById(2L)).thenReturn(child);
    when(departmentMapper.selectAllOrdered()).thenReturn(List.of(root, child));

    assertCode(
        () -> service.changeStatus(2, SystemStatus.ENABLED),
        ExceptionCode.DEPARTMENT_PARENT_UNAVAILABLE);

    child.setStatus(SystemStatus.ENABLED.getCode());
    service.changeStatus(2, SystemStatus.DISABLED);
    verify(departmentMapper).updateById(child);
  }

  /** 禁用部门时存在直接启用子部门应拒绝。 */
  @Test
  void shouldRejectDisablingWithDirectEnabledDescendant() {
    DepartmentEntity parent = department(1, 0, 0);
    when(departmentMapper.selectById(1L)).thenReturn(parent);
    when(departmentMapper.selectAllOrdered()).thenReturn(List.of(parent, department(2, 1, 0)));

    assertCode(
        () -> service.changeStatus(1, SystemStatus.DISABLED),
        ExceptionCode.DEPARTMENT_ENABLED_DESCENDANT_EXISTS);
    verify(departmentMapper, never()).updateById(parent);
  }

  /** 禁用部门时存在深层启用子部门也应拒绝。 */
  @Test
  void shouldRejectDisablingWithDeepEnabledDescendant() {
    DepartmentEntity parent = department(1, 0, 0);
    DepartmentEntity disabledChild = department(2, 1, 0);
    disabledChild.setStatus(SystemStatus.DISABLED.getCode());
    when(departmentMapper.selectById(1L)).thenReturn(parent);
    when(departmentMapper.selectAllOrdered())
        .thenReturn(List.of(parent, disabledChild, department(3, 2, 0)));

    assertCode(
        () -> service.changeStatus(1, SystemStatus.DISABLED),
        ExceptionCode.DEPARTMENT_ENABLED_DESCENDANT_EXISTS);
  }

  /** 所有子孙部门均禁用时允许禁用当前部门。 */
  @Test
  void shouldAllowDisablingWhenAllDescendantsDisabled() {
    DepartmentEntity parent = department(1, 0, 0);
    DepartmentEntity child = department(2, 1, 0);
    DepartmentEntity grandchild = department(3, 2, 0);
    child.setStatus(SystemStatus.DISABLED.getCode());
    grandchild.setStatus(SystemStatus.DISABLED.getCode());
    when(departmentMapper.selectById(1L)).thenReturn(parent);
    when(departmentMapper.selectAllOrdered()).thenReturn(List.of(parent, child, grandchild));

    service.changeStatus(1, SystemStatus.DISABLED);

    verify(departmentMapper).updateById(parent);
  }

  /** 修改接口禁用部门时存在直接启用子部门应拒绝。 */
  @Test
  void updateShouldRejectDisablingWithDirectEnabledDescendant() {
    DepartmentEntity parent = department(1, 0, 0);
    when(departmentMapper.selectAllOrdered()).thenReturn(List.of(parent, department(2, 1, 0)));
    DepartmentUpdateDTO request = updateRequest(1, 0);
    request.setStatus(SystemStatus.DISABLED);

    assertCode(() -> service.update(request), ExceptionCode.DEPARTMENT_ENABLED_DESCENDANT_EXISTS);
    verify(departmentMapper, never()).updateById(parent);
  }

  /** 修改接口禁用部门时存在深层启用子部门也应拒绝。 */
  @Test
  void updateShouldRejectDisablingWithDeepEnabledDescendant() {
    DepartmentEntity parent = department(1, 0, 0);
    DepartmentEntity child = department(2, 1, 0);
    child.setStatus(SystemStatus.DISABLED.getCode());
    when(departmentMapper.selectAllOrdered())
        .thenReturn(List.of(parent, child, department(3, 2, 0)));
    DepartmentUpdateDTO request = updateRequest(1, 0);
    request.setStatus(SystemStatus.DISABLED);

    assertCode(() -> service.update(request), ExceptionCode.DEPARTMENT_ENABLED_DESCENDANT_EXISTS);
  }

  /** 修改接口禁用部门时全部子孙均禁用应允许。 */
  @Test
  void updateShouldAllowDisablingWhenAllDescendantsDisabled() {
    DepartmentEntity parent = department(1, 0, 0);
    DepartmentEntity child = department(2, 1, 0);
    DepartmentEntity grandchild = department(3, 2, 0);
    child.setStatus(SystemStatus.DISABLED.getCode());
    grandchild.setStatus(SystemStatus.DISABLED.getCode());
    when(departmentMapper.selectAllOrdered()).thenReturn(List.of(parent, child, grandchild));
    DepartmentUpdateDTO request = updateRequest(1, 0);
    request.setStatus(SystemStatus.DISABLED);

    service.update(request);

    verify(departmentMapper).updateById(parent);
  }

  /** 新增子部门应按ID升序锁定完整目标祖先链。 */
  @Test
  void createShouldLockTargetAncestorChainInOrder() {
    DepartmentEntity root = department(1, 0, 0);
    DepartmentEntity parent = department(2, 1, 0);
    when(departmentMapper.selectAllOrdered()).thenReturn(List.of(root, parent));

    service.create(createRequest(2));

    verify(departmentMapper).selectByIdsForUpdate(List.of(1L, 2L));
  }

  /** 移动部门应锁定当前部门和新父完整祖先链。 */
  @Test
  void updateShouldLockCurrentAndTargetAncestorsInOrder() {
    DepartmentEntity root = department(1, 0, 0);
    DepartmentEntity parent = department(2, 1, 0);
    DepartmentEntity moving = department(3, 0, 0);
    when(departmentMapper.selectAllOrdered()).thenReturn(List.of(root, parent, moving));

    service.update(updateRequest(3, 2));

    verify(departmentMapper).selectByIdsForUpdate(List.of(1L, 2L, 3L));
  }

  /** 获取行锁后当前部门父级或状态变化应报告并发修改。 */
  @Test
  void updateShouldRejectConcurrentSemanticChangeAfterLock() {
    DepartmentEntity before = department(3, 0, 0);
    DepartmentEntity after = department(3, 9, 0);
    DepartmentEntity concurrentParent = department(9, 0, 0);
    when(departmentMapper.selectAllOrdered())
        .thenReturn(List.of(before), List.of(concurrentParent, after));

    assertCode(
        () -> service.update(updateRequest(3, 0)),
        ExceptionCode.DEPARTMENT_CONCURRENT_MODIFICATION);
    verify(departmentMapper, never()).updateById(after);
  }

  /** 删除应先锁当前部门，再重载部门图并检查关联。 */
  @Test
  void deleteShouldFollowLockAndReloadProtocol() {
    DepartmentEntity target = department(1, 0, 0);
    when(departmentMapper.selectById(1L)).thenReturn(target);
    when(departmentMapper.selectByIdForUpdate(1L)).thenReturn(target);
    when(departmentMapper.selectAllOrdered()).thenReturn(List.of(target));

    service.delete(1);

    var order = Mockito.inOrder(departmentMapper, employeeMapper);
    order.verify(departmentMapper).selectByIdForUpdate(1L);
    order.verify(departmentMapper).selectAllOrdered();
    order.verify(employeeMapper).countByDepartmentId(1L);
    order.verify(departmentMapper).deleteById(1L);
  }

  /** 部门写操作事务隔离级别应为READ_COMMITTED。 */
  @Test
  void writeOperationsShouldUseReadCommittedIsolation() throws Exception {
    for (String method : List.of("create", "update", "changeStatus", "delete")) {
      Class<?>[] parameterTypes =
          switch (method) {
            case "create" -> new Class<?>[] {DepartmentCreateDTO.class};
            case "update" -> new Class<?>[] {DepartmentUpdateDTO.class};
            case "changeStatus" -> new Class<?>[] {long.class, SystemStatus.class};
            default -> new Class<?>[] {long.class};
          };
      Transactional transactional =
          DepartmentService.class
              .getMethod(method, parameterTypes)
              .getAnnotation(Transactional.class);
      assertThat(transactional).isNotNull();
      assertThat(transactional.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }
  }

  /** 禁用祖先下未移动的部门应允许修改名称和排序。 */
  @Test
  void shouldAllowNonMovingEditUnderDisabledAncestor() {
    DepartmentEntity root = department(1, 0, 0);
    root.setStatus(SystemStatus.DISABLED.getCode());
    DepartmentEntity child = department(2, 1, 0);
    when(departmentMapper.selectAllOrdered()).thenReturn(List.of(root, child));

    DepartmentUpdateDTO request = updateRequest(2, 1);
    request.setName("新名称");
    request.setSortOrder(9);

    service.update(request);

    verify(departmentMapper).updateById(child);
    assertThat(child.getName()).isEqualTo("新名称");
    assertThat(child.getSortOrder()).isEqualTo(9);
  }

  /** 禁用祖先下未移动的部门应允许将自身改为禁用。 */
  @Test
  void shouldAllowDisablingUnderDisabledAncestor() {
    DepartmentEntity root = department(1, 0, 0);
    root.setStatus(SystemStatus.DISABLED.getCode());
    DepartmentEntity child = department(2, 1, 0);
    when(departmentMapper.selectAllOrdered()).thenReturn(List.of(root, child));

    DepartmentUpdateDTO request = updateRequest(2, 1);
    request.setStatus(SystemStatus.DISABLED);

    service.update(request);

    verify(departmentMapper).updateById(child);
    assertThat(child.getStatus()).isEqualTo(SystemStatus.DISABLED.getCode());
  }

  /** 禁用祖先下的部门不得通过修改接口重新启用。 */
  @Test
  void shouldRejectReEnablingUnderDisabledAncestor() {
    DepartmentEntity root = department(1, 0, 0);
    root.setStatus(SystemStatus.DISABLED.getCode());
    DepartmentEntity child = department(2, 1, 0);
    child.setStatus(SystemStatus.DISABLED.getCode());
    when(departmentMapper.selectAllOrdered()).thenReturn(List.of(root, child));

    assertCode(
        () -> service.update(updateRequest(2, 1)), ExceptionCode.DEPARTMENT_PARENT_UNAVAILABLE);
  }

  /** 修改部门不得移动到自身后代。 */
  @Test
  void shouldRejectMoveToDescendant() {
    when(departmentMapper.selectAllOrdered())
        .thenReturn(List.of(department(1, 0, 0), department(2, 1, 0)));
    DepartmentUpdateDTO request = updateRequest(1, 2);
    assertCode(() -> service.update(request), ExceptionCode.DEPARTMENT_CYCLE);
  }

  /** 移动后整个子树深度不得超过64层。 */
  @Test
  void shouldRejectMoveWhenSubtreeExceedsDepth() {
    List<DepartmentEntity> departments = new ArrayList<>();
    for (int id = 1; id <= 63; id++) {
      departments.add(department(id, id - 1, 0));
    }
    departments.add(department(100, 0, 0));
    departments.add(department(101, 100, 0));
    when(departmentMapper.selectAllOrdered()).thenReturn(departments);
    assertCode(
        () -> service.update(updateRequest(100, 63)), ExceptionCode.DEPARTMENT_DEPTH_EXCEEDED);
  }

  /** 删除部门前应分别校验子部门和员工。 */
  @Test
  void shouldRejectDeleteWithChildrenOrEmployees() {
    DepartmentEntity target = department(1, 0, 0);
    DepartmentEntity child = department(2, 1, 0);
    when(departmentMapper.selectById(1L)).thenReturn(target);
    when(departmentMapper.selectByIdForUpdate(1L)).thenReturn(target);
    when(departmentMapper.selectAllOrdered()).thenReturn(List.of(target, child), List.of(target));
    assertCode(() -> service.delete(1), ExceptionCode.DEPARTMENT_HAS_CHILDREN);

    when(employeeMapper.countByDepartmentId(1L)).thenReturn(1L);
    assertCode(() -> service.delete(1), ExceptionCode.DEPARTMENT_HAS_EMPLOYEES);
  }

  /** 唯一索引并发冲突应转换为明确业务异常，未知索引应透传。 */
  @Test
  void shouldTranslateOnlyDepartmentNameUniqueConflict() {
    when(departmentMapper.selectAllOrdered()).thenReturn(List.of());
    when(departmentMapper.insert(any(DepartmentEntity.class)))
        .thenThrow(
            new DuplicateKeyException("Duplicate entry for key 'udx_department_parent_name'"));
    assertCode(() -> service.create(createRequest(0)), ExceptionCode.DEPARTMENT_NAME_DUPLICATED);

    when(departmentMapper.insert(any(DepartmentEntity.class)))
        .thenThrow(new DuplicateKeyException("Duplicate entry for key 'other_index'"));
    assertThatThrownBy(() -> service.create(createRequest(0)))
        .isInstanceOf(DuplicateKeyException.class);
  }

  private DepartmentCreateDTO createRequest(long parentId) {
    DepartmentCreateDTO request = new DepartmentCreateDTO();
    request.setParentId(parentId);
    request.setName("研发部");
    request.setStatus(SystemStatus.ENABLED);
    return request;
  }

  private DepartmentUpdateDTO updateRequest(long id, long parentId) {
    DepartmentUpdateDTO request = new DepartmentUpdateDTO();
    request.setId(id);
    request.setParentId(parentId);
    request.setName("研发部");
    request.setStatus(SystemStatus.ENABLED);
    return request;
  }

  private DepartmentEntity department(long id, long parentId, int sortOrder) {
    DepartmentEntity result = new DepartmentEntity();
    result.setId(id);
    result.setParentId(parentId);
    result.setName("部门" + id);
    result.setSortOrder(sortOrder);
    result.setStatus(SystemStatus.ENABLED.getCode());
    return result;
  }

  private void assertCode(Runnable action, ExceptionCode code) {
    assertThatThrownBy(action::run)
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getExceptionCode())
        .isEqualTo(code);
  }
}
