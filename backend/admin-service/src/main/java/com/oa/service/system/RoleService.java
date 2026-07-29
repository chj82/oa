package com.oa.service.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.RoleCreateDTO;
import com.oa.common.model.system.dto.RoleQueryDTO;
import com.oa.common.model.system.dto.RoleUpdateDTO;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.model.system.vo.RoleVO;
import com.oa.common.response.PageResult;
import com.oa.dao.system.EmployeeRoleMapper;
import com.oa.dao.system.RoleMapper;
import com.oa.dao.system.RoleResourceMapper;
import com.oa.dao.system.SystemResourceMapper;
import com.oa.entity.system.RoleEntity;
import com.oa.entity.system.RoleResourceEntity;
import com.oa.entity.system.SystemResourceEntity;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 角色管理服务。 */
@Service
public class RoleService {
  private static final int RELATION_BATCH_SIZE = 500;
  private static final String CODE_UNIQUE_INDEX = "udx_role_code";
  private static final String NAME_UNIQUE_INDEX = "udx_role_name";

  private final RoleMapper roleMapper;
  private final EmployeeRoleMapper employeeRoleMapper;
  private final RoleResourceMapper roleResourceMapper;
  private final SystemResourceMapper systemResourceMapper;
  private final PermissionService permissionService;

  public RoleService(
      RoleMapper roleMapper,
      EmployeeRoleMapper employeeRoleMapper,
      RoleResourceMapper roleResourceMapper,
      SystemResourceMapper systemResourceMapper,
      PermissionService permissionService) {
    this.roleMapper = roleMapper;
    this.employeeRoleMapper = employeeRoleMapper;
    this.roleResourceMapper = roleResourceMapper;
    this.systemResourceMapper = systemResourceMapper;
    this.permissionService = permissionService;
  }

  /** 分页查询角色。 */
  public PageResult<RoleVO> page(RoleQueryDTO query) {
    IPage<RoleEntity> entityPage = roleMapper.selectRolePage(query);
    PageResult<RoleVO> result = new PageResult<>();
    result.setRecords(entityPage.getRecords().stream().map(this::toVO).toList());
    result.setTotal(entityPage.getTotal());
    result.setPage(entityPage.getCurrent());
    result.setSize(entityPage.getSize());
    return result;
  }

  /** 查询角色详情。 */
  public RoleVO detail(long roleId) {
    return toVO(requiredRole(roleId));
  }

  /** 查询角色已授权的资源ID。 */
  public List<Long> resourceIds(long roleId) {
    requiredRole(roleId);
    return roleResourceMapper.selectResourceIdsByRoleId(roleId);
  }

  /** 新增角色。 */
  @Transactional
  public RoleVO create(RoleCreateDTO request) {
    validateUnique(request.getCode().strip(), request.getName().strip(), null);
    LocalDateTime now = LocalDateTime.now();
    RoleEntity role = new RoleEntity();
    copyFields(request, role);
    role.setCreatedAt(now);
    role.setUpdatedAt(now);
    try {
      roleMapper.insert(role);
    } catch (DuplicateKeyException exception) {
      throw translateDuplicateKey(exception);
    }
    return toVO(role);
  }

  /** 修改角色。 */
  @Transactional
  public RoleVO update(RoleUpdateDTO request) {
    RoleEntity role = requiredRole(request.getId());
    validateUnique(request.getCode().strip(), request.getName().strip(), role.getId());
    boolean statusChanged = role.getStatus() != request.getStatus().getCode();
    int expectedStatus = role.getStatus();
    copyFields(request, role);
    role.setUpdatedAt(LocalDateTime.now());
    if (statusChanged) {
      permissionService.invalidateAll();
    }
    try {
      if (roleMapper.updateBySnapshot(role, expectedStatus) == 0) {
        throw new BusinessException(ExceptionCode.ROLE_CONCURRENT_MODIFICATION);
      }
    } catch (DuplicateKeyException exception) {
      throw translateDuplicateKey(exception);
    }
    return toVO(role);
  }

  /** 修改角色状态。 */
  @Transactional
  public void changeStatus(long roleId, SystemStatus status) {
    RoleEntity role = requiredRole(roleId);
    if (role.getStatus() == status.getCode()) {
      requireCurrentSnapshot(role);
      return;
    }
    int expectedStatus = role.getStatus();
    role.setStatus(status.getCode());
    role.setUpdatedAt(LocalDateTime.now());
    permissionService.invalidateAll();
    if (roleMapper.updateBySnapshot(role, expectedStatus) == 0) {
      throw new BusinessException(ExceptionCode.ROLE_CONCURRENT_MODIFICATION);
    }
  }

  /** 删除未关联员工的角色。 */
  @Transactional
  public void delete(long roleId) {
    permissionService.invalidateAll();
    requiredLockedRole(roleId);
    if (employeeRoleMapper.countByRoleId(roleId) > 0) {
      throw new BusinessException(ExceptionCode.ROLE_HAS_EMPLOYEES);
    }
    roleResourceMapper.deleteByRoleId(roleId);
    roleMapper.deleteById(roleId);
  }

  /** 全量保存角色资源，空列表表示清空。 */
  @Transactional
  public void saveResources(long roleId, List<Long> resourceIds) {
    permissionService.invalidateAll();
    requiredLockedRole(roleId);
    List<Long> distinctIds = new LinkedHashSet<>(resourceIds).stream().toList();
    List<SystemResourceEntity> lockedResources =
        distinctIds.isEmpty()
            ? List.of()
            : systemResourceMapper.selectByIdsForUpdate(distinctIds.stream().sorted().toList());
    Set<Long> enabledIds =
        lockedResources.stream()
            .filter(resource -> resource.getStatus() == SystemStatus.ENABLED.getCode())
            .map(SystemResourceEntity::getId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    if (!enabledIds.equals(new LinkedHashSet<>(distinctIds))) {
      throw new BusinessException(ExceptionCode.ROLE_RESOURCE_UNAVAILABLE);
    }
    roleResourceMapper.deleteByRoleId(roleId);
    insertResourceRelations(roleId, distinctIds);
  }

  private void validateUnique(String code, String name, Long roleId) {
    rejectDuplicate(roleMapper.selectByCode(code), roleId, ExceptionCode.ROLE_CODE_DUPLICATED);
    rejectDuplicate(roleMapper.selectByName(name), roleId, ExceptionCode.ROLE_NAME_DUPLICATED);
  }

  private void rejectDuplicate(RoleEntity duplicate, Long roleId, ExceptionCode exceptionCode) {
    if (duplicate != null && !Objects.equals(duplicate.getId(), roleId)) {
      throw new BusinessException(exceptionCode);
    }
  }

  private void copyFields(RoleCreateDTO request, RoleEntity role) {
    role.setCode(request.getCode().strip());
    role.setName(request.getName().strip());
    role.setDescription(nullableText(request.getDescription()));
    role.setStatus(request.getStatus().getCode());
  }

  private RoleEntity requiredRole(long roleId) {
    RoleEntity role = roleMapper.selectById(roleId);
    if (role == null) {
      throw new BusinessException(ExceptionCode.ROLE_NOT_FOUND);
    }
    return role;
  }

  private RoleEntity requiredLockedRole(long roleId) {
    RoleEntity role = roleMapper.selectByIdForUpdate(roleId);
    if (role == null) {
      throw new BusinessException(ExceptionCode.ROLE_NOT_FOUND);
    }
    return role;
  }

  private void requireCurrentSnapshot(RoleEntity role) {
    if (!roleMapper.matchesSnapshot(role.getId(), role.getStatus())) {
      throw new BusinessException(ExceptionCode.ROLE_CONCURRENT_MODIFICATION);
    }
  }

  private void insertResourceRelations(long roleId, List<Long> resourceIds) {
    if (resourceIds.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    for (int start = 0; start < resourceIds.size(); start += RELATION_BATCH_SIZE) {
      int end = Math.min(start + RELATION_BATCH_SIZE, resourceIds.size());
      List<RoleResourceEntity> relations =
          resourceIds.subList(start, end).stream()
              .map(resourceId -> roleResource(roleId, resourceId, now))
              .toList();
      roleResourceMapper.insertBatch(relations);
    }
  }

  private RoleResourceEntity roleResource(long roleId, long resourceId, LocalDateTime createdAt) {
    RoleResourceEntity relation = new RoleResourceEntity();
    relation.setRoleId(roleId);
    relation.setResourceId(resourceId);
    relation.setCreatedAt(createdAt);
    return relation;
  }

  private RuntimeException translateDuplicateKey(DuplicateKeyException exception) {
    String message = exception.getMessage();
    if (message != null && message.contains(CODE_UNIQUE_INDEX)) {
      return new BusinessException(ExceptionCode.ROLE_CODE_DUPLICATED);
    }
    if (message != null && message.contains(NAME_UNIQUE_INDEX)) {
      return new BusinessException(ExceptionCode.ROLE_NAME_DUPLICATED);
    }
    return exception;
  }

  private String nullableText(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private RoleVO toVO(RoleEntity role) {
    RoleVO result = new RoleVO();
    result.setId(role.getId());
    result.setCode(role.getCode());
    result.setName(role.getName());
    result.setDescription(role.getDescription());
    result.setStatus(
        role.getStatus() == SystemStatus.ENABLED.getCode()
            ? SystemStatus.ENABLED
            : SystemStatus.DISABLED);
    result.setCreatedAt(role.getCreatedAt());
    result.setUpdatedAt(role.getUpdatedAt());
    return result;
  }
}
