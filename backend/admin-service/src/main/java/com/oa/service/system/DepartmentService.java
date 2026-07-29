package com.oa.service.system;

import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.DepartmentCreateDTO;
import com.oa.common.model.system.dto.DepartmentUpdateDTO;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.model.system.vo.DepartmentVO;
import com.oa.dao.system.DepartmentMapper;
import com.oa.dao.system.EmployeeMapper;
import com.oa.entity.system.DepartmentEntity;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 部门管理服务。 */
@Service
public class DepartmentService {
  private static final int MAX_DEPTH = 64;
  private static final String NAME_UNIQUE_INDEX = "udx_department_parent_name";

  private final DepartmentMapper departmentMapper;
  private final EmployeeMapper employeeMapper;

  public DepartmentService(DepartmentMapper departmentMapper, EmployeeMapper employeeMapper) {
    this.departmentMapper = departmentMapper;
    this.employeeMapper = employeeMapper;
  }

  /** 查询完整部门树。 */
  public List<DepartmentVO> tree() {
    DepartmentGraph graph = buildGraph(departmentMapper.selectAllOrdered());
    List<DepartmentVO> roots = new ArrayList<>();
    for (DepartmentEntity root : graph.childrenByParent().getOrDefault(0L, List.of())) {
      roots.add(toTree(root, graph.childrenByParent(), 1));
    }
    return roots;
  }

  /** 查询部门详情。 */
  public DepartmentVO detail(long departmentId) {
    return toVO(requiredDepartment(departmentId));
  }

  /** 新增部门。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public DepartmentVO create(DepartmentCreateDTO request) {
    List<DepartmentEntity> departments = departmentMapper.selectAllOrdered();
    DepartmentGraph graph = buildGraph(departments);
    if (request.getParentId() != 0) {
      List<Long> lockIds = ancestorIds(request.getParentId(), graph.byId());
      departmentMapper.selectByIdsForUpdate(lockIds);
      DepartmentGraph lockedGraph = buildGraph(departmentMapper.selectAllOrdered());
      ensureSemanticsUnchanged(graph, lockedGraph, lockIds);
      graph = lockedGraph;
    }
    validateParent(request.getParentId(), graph.byId());
    int parentDepth = depthOf(request.getParentId(), graph.byId());
    if (parentDepth + 1 > MAX_DEPTH) {
      throw new BusinessException(ExceptionCode.DEPARTMENT_DEPTH_EXCEEDED);
    }
    String name = request.getName().strip();
    validateUnique(request.getParentId(), name, null);
    LocalDateTime now = LocalDateTime.now();
    DepartmentEntity department = new DepartmentEntity();
    copyFields(
        request.getParentId(), name, request.getSortOrder(), request.getStatus(), department);
    department.setCreatedAt(now);
    department.setUpdatedAt(now);
    insert(department);
    return toVO(department);
  }

  /** 修改部门。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public DepartmentVO update(DepartmentUpdateDTO request) {
    List<DepartmentEntity> departments = departmentMapper.selectAllOrdered();
    DepartmentGraph snapshot = buildGraph(departments);
    DepartmentEntity snapshotDepartment = snapshot.byId().get(request.getId());
    if (snapshotDepartment == null) {
      throw new BusinessException(ExceptionCode.DEPARTMENT_NOT_FOUND);
    }
    TreeSet<Long> lockIdSet = new TreeSet<>(ancestorIds(request.getParentId(), snapshot.byId()));
    lockIdSet.add(request.getId());
    List<Long> lockIds = List.copyOf(lockIdSet);
    departmentMapper.selectByIdsForUpdate(lockIds);
    DepartmentGraph graph = buildGraph(departmentMapper.selectAllOrdered());
    ensureSemanticsUnchanged(snapshot, graph, lockIds);
    DepartmentEntity department = graph.byId().get(request.getId());
    if (Objects.equals(request.getId(), request.getParentId())
        || isDescendant(request.getParentId(), request.getId(), graph.byId())) {
      throw new BusinessException(ExceptionCode.DEPARTMENT_CYCLE);
    }
    boolean parentChanged = department.getParentId() != request.getParentId();
    if (parentChanged) {
      validateParent(request.getParentId(), graph.byId());
    }
    validateStatusChange(
        department, request.getStatus(), request.getParentId(), graph, parentChanged);
    int parentDepth = depthOf(request.getParentId(), graph.byId());
    int subtreeHeight = subtreeHeight(department.getId(), graph.childrenByParent(), 1);
    if (parentDepth + subtreeHeight > MAX_DEPTH) {
      throw new BusinessException(ExceptionCode.DEPARTMENT_DEPTH_EXCEEDED);
    }
    String name = request.getName().strip();
    validateUnique(request.getParentId(), name, department.getId());
    copyFields(
        request.getParentId(), name, request.getSortOrder(), request.getStatus(), department);
    department.setUpdatedAt(LocalDateTime.now());
    update(department);
    return toVO(department);
  }

  /** 修改部门状态。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void changeStatus(long departmentId, SystemStatus status) {
    DepartmentEntity department = requiredDepartment(departmentId);
    if (department.getStatus() == status.getCode()) {
      return;
    }
    departmentMapper.selectByIdForUpdate(departmentId);
    DepartmentGraph graph = buildGraph(departmentMapper.selectAllOrdered());
    ensureSemanticsUnchanged(
        new DepartmentGraph(Map.of(departmentId, department), Map.of()),
        graph,
        List.of(departmentId));
    department = graph.byId().get(departmentId);
    validateStatusChange(department, status, department.getParentId(), graph, false);
    department.setStatus(status.getCode());
    department.setUpdatedAt(LocalDateTime.now());
    departmentMapper.updateById(department);
  }

  /** 删除没有子部门和员工的部门。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void delete(long departmentId) {
    DepartmentEntity snapshot = requiredDepartment(departmentId);
    departmentMapper.selectByIdForUpdate(departmentId);
    DepartmentGraph graph = buildGraph(departmentMapper.selectAllOrdered());
    ensureSemanticsUnchanged(
        new DepartmentGraph(Map.of(departmentId, snapshot), Map.of()),
        graph,
        List.of(departmentId));
    if (!graph.childrenByParent().getOrDefault(departmentId, List.of()).isEmpty()) {
      throw new BusinessException(ExceptionCode.DEPARTMENT_HAS_CHILDREN);
    }
    if (employeeMapper.countByDepartmentId(departmentId) > 0) {
      throw new BusinessException(ExceptionCode.DEPARTMENT_HAS_EMPLOYEES);
    }
    departmentMapper.deleteById(departmentId);
  }

  private DepartmentGraph buildGraph(List<DepartmentEntity> departments) {
    Map<Long, DepartmentEntity> byId = new HashMap<>();
    Map<Long, List<DepartmentEntity>> childrenByParent = new HashMap<>();
    for (DepartmentEntity department : departments) {
      byId.put(department.getId(), department);
      childrenByParent
          .computeIfAbsent(department.getParentId(), key -> new ArrayList<>())
          .add(department);
    }
    Comparator<DepartmentEntity> order =
        Comparator.comparingInt(DepartmentEntity::getSortOrder)
            .thenComparingLong(DepartmentEntity::getId);
    childrenByParent.values().forEach(children -> children.sort(order));
    for (DepartmentEntity department : departments) {
      if (department.getParentId() != 0 && !byId.containsKey(department.getParentId())) {
        throw new BusinessException(ExceptionCode.DEPARTMENT_PARENT_UNAVAILABLE);
      }
      depthOf(department.getId(), byId);
    }
    return new DepartmentGraph(byId, childrenByParent);
  }

  private List<Long> ancestorIds(long departmentId, Map<Long, DepartmentEntity> byId) {
    TreeSet<Long> ids = new TreeSet<>();
    long currentId = departmentId;
    while (currentId != 0) {
      DepartmentEntity current = byId.get(currentId);
      if (current == null) {
        throw new BusinessException(ExceptionCode.DEPARTMENT_PARENT_UNAVAILABLE);
      }
      ids.add(currentId);
      currentId = current.getParentId();
    }
    return List.copyOf(ids);
  }

  private void ensureSemanticsUnchanged(
      DepartmentGraph before, DepartmentGraph after, List<Long> lockedIds) {
    for (Long id : lockedIds) {
      DepartmentEntity previous = before.byId().get(id);
      DepartmentEntity current = after.byId().get(id);
      if (previous == null
          || current == null
          || previous.getParentId() != current.getParentId()
          || previous.getStatus() != current.getStatus()) {
        throw new BusinessException(ExceptionCode.DEPARTMENT_CONCURRENT_MODIFICATION);
      }
    }
  }

  private int depthOf(long departmentId, Map<Long, DepartmentEntity> byId) {
    int depth = 0;
    long currentId = departmentId;
    Map<Long, Boolean> visited = new HashMap<>();
    while (currentId != 0) {
      if (visited.put(currentId, Boolean.TRUE) != null) {
        throw new BusinessException(ExceptionCode.DEPARTMENT_CYCLE);
      }
      DepartmentEntity current = byId.get(currentId);
      if (current == null) {
        throw new BusinessException(ExceptionCode.DEPARTMENT_PARENT_UNAVAILABLE);
      }
      depth++;
      if (depth > MAX_DEPTH) {
        throw new BusinessException(ExceptionCode.DEPARTMENT_DEPTH_EXCEEDED);
      }
      currentId = current.getParentId();
    }
    return depth;
  }

  private void validateParent(long parentId, Map<Long, DepartmentEntity> byId) {
    long currentId = parentId;
    int depth = 0;
    while (currentId != 0) {
      DepartmentEntity parent = byId.get(currentId);
      if (parent == null || parent.getStatus() != SystemStatus.ENABLED.getCode()) {
        throw new BusinessException(ExceptionCode.DEPARTMENT_PARENT_UNAVAILABLE);
      }
      if (++depth > MAX_DEPTH) {
        throw new BusinessException(ExceptionCode.DEPARTMENT_DEPTH_EXCEEDED);
      }
      currentId = parent.getParentId();
    }
  }

  private boolean isDescendant(
      long candidateId, long ancestorId, Map<Long, DepartmentEntity> byId) {
    long currentId = candidateId;
    while (currentId != 0) {
      if (currentId == ancestorId) {
        return true;
      }
      DepartmentEntity current = byId.get(currentId);
      if (current == null) {
        return false;
      }
      currentId = current.getParentId();
    }
    return false;
  }

  private int subtreeHeight(
      long id, Map<Long, List<DepartmentEntity>> childrenByParent, int depth) {
    int maximum = depth;
    for (DepartmentEntity child : childrenByParent.getOrDefault(id, List.of())) {
      maximum = Math.max(maximum, subtreeHeight(child.getId(), childrenByParent, depth + 1));
    }
    return maximum;
  }

  private void validateStatusChange(
      DepartmentEntity department,
      SystemStatus targetStatus,
      long targetParentId,
      DepartmentGraph graph,
      boolean parentValidated) {
    if (department.getStatus() == targetStatus.getCode()) {
      return;
    }
    if (targetStatus == SystemStatus.ENABLED) {
      if (!parentValidated) {
        validateParent(targetParentId, graph.byId());
      }
      return;
    }
    ensureNoEnabledDescendant(department.getId(), graph.childrenByParent());
  }

  private void ensureNoEnabledDescendant(
      long departmentId, Map<Long, List<DepartmentEntity>> childrenByParent) {
    ArrayDeque<DepartmentEntity> pending =
        new ArrayDeque<>(childrenByParent.getOrDefault(departmentId, List.of()));
    while (!pending.isEmpty()) {
      DepartmentEntity current = pending.removeFirst();
      if (current.getStatus() == SystemStatus.ENABLED.getCode()) {
        throw new BusinessException(ExceptionCode.DEPARTMENT_ENABLED_DESCENDANT_EXISTS);
      }
      pending.addAll(childrenByParent.getOrDefault(current.getId(), List.of()));
    }
  }

  private DepartmentVO toTree(
      DepartmentEntity entity, Map<Long, List<DepartmentEntity>> childrenByParent, int depth) {
    if (depth > MAX_DEPTH) {
      throw new BusinessException(ExceptionCode.DEPARTMENT_DEPTH_EXCEEDED);
    }
    DepartmentVO result = toVO(entity);
    List<DepartmentVO> children = new ArrayList<>();
    for (DepartmentEntity child : childrenByParent.getOrDefault(entity.getId(), List.of())) {
      children.add(toTree(child, childrenByParent, depth + 1));
    }
    result.setChildren(children);
    return result;
  }

  private void validateUnique(long parentId, String name, Long excludedId) {
    DepartmentEntity duplicate = departmentMapper.selectByParentIdAndName(parentId, name);
    if (duplicate != null && !Objects.equals(duplicate.getId(), excludedId)) {
      throw new BusinessException(ExceptionCode.DEPARTMENT_NAME_DUPLICATED);
    }
  }

  private DepartmentEntity requiredDepartment(long id) {
    DepartmentEntity department = departmentMapper.selectById(id);
    if (department == null) {
      throw new BusinessException(ExceptionCode.DEPARTMENT_NOT_FOUND);
    }
    return department;
  }

  private void insert(DepartmentEntity entity) {
    try {
      departmentMapper.insert(entity);
    } catch (DuplicateKeyException exception) {
      throw translateDuplicate(exception);
    }
  }

  private void update(DepartmentEntity entity) {
    try {
      departmentMapper.updateById(entity);
    } catch (DuplicateKeyException exception) {
      throw translateDuplicate(exception);
    }
  }

  private RuntimeException translateDuplicate(DuplicateKeyException exception) {
    String message = exception.getMostSpecificCause().getMessage();
    if (message != null && message.contains(NAME_UNIQUE_INDEX)) {
      return new BusinessException(ExceptionCode.DEPARTMENT_NAME_DUPLICATED);
    }
    return exception;
  }

  private void copyFields(
      long parentId, String name, int sortOrder, SystemStatus status, DepartmentEntity target) {
    target.setParentId(parentId);
    target.setName(name);
    target.setSortOrder(sortOrder);
    target.setStatus(status.getCode());
  }

  private DepartmentVO toVO(DepartmentEntity entity) {
    DepartmentVO result = new DepartmentVO();
    result.setId(entity.getId());
    result.setParentId(entity.getParentId());
    result.setName(entity.getName());
    result.setSortOrder(entity.getSortOrder());
    result.setStatus(
        entity.getStatus() == SystemStatus.ENABLED.getCode()
            ? SystemStatus.ENABLED
            : SystemStatus.DISABLED);
    return result;
  }

  private static final class DepartmentGraph {
    private final Map<Long, DepartmentEntity> byId;
    private final Map<Long, List<DepartmentEntity>> childrenByParent;

    private DepartmentGraph(
        Map<Long, DepartmentEntity> byId, Map<Long, List<DepartmentEntity>> childrenByParent) {
      this.byId = byId;
      this.childrenByParent = childrenByParent;
    }

    private Map<Long, DepartmentEntity> byId() {
      return byId;
    }

    private Map<Long, List<DepartmentEntity>> childrenByParent() {
      return childrenByParent;
    }
  }
}
