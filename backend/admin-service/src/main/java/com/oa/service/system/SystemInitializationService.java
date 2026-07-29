package com.oa.service.system;

import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.enums.ResourceType;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.dao.system.DepartmentMapper;
import com.oa.dao.system.EmployeeMapper;
import com.oa.dao.system.ResourceApiMapper;
import com.oa.dao.system.SystemApiMapper;
import com.oa.dao.system.SystemResourceMapper;
import com.oa.entity.system.DepartmentEntity;
import com.oa.entity.system.EmployeeEntity;
import com.oa.entity.system.ResourceApiEntity;
import com.oa.entity.system.SystemApiEntity;
import com.oa.entity.system.SystemResourceEntity;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 系统基础数据初始化服务。 */
@Service
public class SystemInitializationService {
  private static final String ROOT_DEPARTMENT_NAME = "总部";
  private static final String ROOT_RESOURCE_NAME = "系统管理";
  private static final int BATCH_SIZE = 500;
  private static final String DEPARTMENT_UNIQUE_INDEX = "udx_department_parent_name";
  private static final String RESOURCE_CODE_INDEX = "udx_system_resource_code";
  private static final String RESOURCE_PATH_INDEX = "udx_system_resource_path";
  private static final String RESOURCE_API_INDEX = "udx_resource_api_resource_api";
  private static final String EMPLOYEE_USERNAME_INDEX = "udx_employee_username";
  private static final List<ResourceDefinition> DEFINITIONS = definitions();

  private final DepartmentMapper departmentMapper;
  private final SystemResourceMapper resourceMapper;
  private final SystemApiMapper apiMapper;
  private final ResourceApiMapper resourceApiMapper;
  private final EmployeeMapper employeeMapper;
  private final PermissionService permissionService;
  private final BCryptPasswordEncoder passwordEncoder;

  public SystemInitializationService(
      DepartmentMapper departmentMapper,
      SystemResourceMapper resourceMapper,
      SystemApiMapper apiMapper,
      ResourceApiMapper resourceApiMapper,
      EmployeeMapper employeeMapper,
      PermissionService permissionService,
      BCryptPasswordEncoder passwordEncoder) {
    this.departmentMapper = departmentMapper;
    this.resourceMapper = resourceMapper;
    this.apiMapper = apiMapper;
    this.resourceApiMapper = resourceApiMapper;
    this.employeeMapper = employeeMapper;
    this.permissionService = permissionService;
    this.passwordEncoder = passwordEncoder;
  }

  /** 在单一事务内补齐系统基础数据。 */
  @Transactional
  public void initialize(String administratorUsername, String administratorPassword) {
    String username = validateUsername(administratorUsername);
    String password = validatePassword(administratorPassword);
    Map<String, SystemApiEntity> apis = requireApis();
    boolean invalidationPlanned = requiresPermissionInvalidation(apis);
    if (invalidationPlanned) {
      permissionService.invalidateAll();
    }
    DepartmentEntity department = ensureRootDepartment();
    Map<String, SystemResourceEntity> resources = ensureResources();
    ensureRelations(resources, apis, invalidationPlanned);
    ensureAdministrator(username, password, department.getId());
  }

  private boolean requiresPermissionInvalidation(Map<String, SystemApiEntity> apis) {
    for (ResourceDefinition definition : DEFINITIONS) {
      if (definition.apiPath == null) {
        continue;
      }
      SystemResourceEntity resource = resourceMapper.selectByCode(definition.code);
      if (resource == null) {
        return true;
      }
      long apiId = apis.get(definition.apiPath).getId();
      if (!resourceApiMapper.selectApiIdsByResourceId(resource.getId()).contains(apiId)) {
        return true;
      }
    }
    return false;
  }

  private Map<String, SystemApiEntity> requireApis() {
    Map<String, SystemApiEntity> apis = new HashMap<>();
    for (ResourceDefinition definition : DEFINITIONS) {
      if (definition.apiPath == null || apis.containsKey(definition.apiPath)) {
        continue;
      }
      SystemApiEntity api = apiMapper.selectByPath(definition.apiPath);
      if (api == null) {
        throw new BusinessException(
            ExceptionCode.INITIALIZATION_API_MISSING,
            ExceptionCode.INITIALIZATION_API_MISSING.getName() + "：" + definition.apiPath);
      }
      apis.put(definition.apiPath, api);
    }
    return apis;
  }

  private DepartmentEntity ensureRootDepartment() {
    DepartmentEntity department =
        departmentMapper.selectByParentIdAndName(0L, ROOT_DEPARTMENT_NAME);
    if (department != null) {
      return department;
    }
    LocalDateTime now = LocalDateTime.now();
    department = new DepartmentEntity();
    department.setParentId(0L);
    department.setName(ROOT_DEPARTMENT_NAME);
    department.setSortOrder(0);
    department.setStatus(SystemStatus.ENABLED.getCode());
    department.setCreatedAt(now);
    department.setUpdatedAt(now);
    try {
      departmentMapper.insert(department);
    } catch (DuplicateKeyException exception) {
      throw translateConflict(exception, DEPARTMENT_UNIQUE_INDEX);
    }
    return department;
  }

  private Map<String, SystemResourceEntity> ensureResources() {
    Map<String, SystemResourceEntity> resources = new HashMap<>();
    for (ResourceDefinition definition : DEFINITIONS) {
      long parentId =
          definition.parentCode == null ? 0L : resources.get(definition.parentCode).getId();
      SystemResourceEntity resource =
          definition.parentCode == null
              ? resourceMapper.selectByParentIdAndName(0L, ROOT_RESOURCE_NAME)
              : resourceMapper.selectByCode(definition.code);
      if (resource == null) {
        resource = newResource(definition, parentId);
        try {
          resourceMapper.insert(resource);
        } catch (DuplicateKeyException exception) {
          throw translateConflict(exception, RESOURCE_CODE_INDEX, RESOURCE_PATH_INDEX);
        }
      } else if (!definition.type.getCode().equals(resource.getType())
          || resource.getParentId() != parentId) {
        throw new BusinessException(ExceptionCode.INITIALIZATION_RESOURCE_CONFLICT);
      }
      resources.put(definition.code, resource);
    }
    return resources;
  }

  private SystemResourceEntity newResource(ResourceDefinition definition, long parentId) {
    LocalDateTime now = LocalDateTime.now();
    SystemResourceEntity resource = new SystemResourceEntity();
    resource.setParentId(parentId);
    resource.setType(definition.type.getCode());
    resource.setName(definition.name);
    resource.setCode(definition.code);
    resource.setPath(definition.menuPath);
    resource.setIcon(null);
    resource.setSortOrder(definition.sortOrder);
    resource.setVisible(definition.type == ResourceType.ACTION ? 0 : 1);
    resource.setStatus(SystemStatus.ENABLED.getCode());
    resource.setCreatedAt(now);
    resource.setUpdatedAt(now);
    return resource;
  }

  private void ensureRelations(
      Map<String, SystemResourceEntity> resources,
      Map<String, SystemApiEntity> apis,
      boolean invalidationPlanned) {
    List<ResourceApiEntity> missing = new ArrayList<>();
    LocalDateTime now = LocalDateTime.now();
    for (ResourceDefinition definition : DEFINITIONS) {
      if (definition.apiPath == null) {
        continue;
      }
      long resourceId = resources.get(definition.code).getId();
      long apiId = apis.get(definition.apiPath).getId();
      Set<Long> existing = new HashSet<>(resourceApiMapper.selectApiIdsByResourceId(resourceId));
      if (!existing.contains(apiId)) {
        ResourceApiEntity relation = new ResourceApiEntity();
        relation.setResourceId(resourceId);
        relation.setApiId(apiId);
        relation.setCreatedAt(now);
        missing.add(relation);
      }
    }
    if (!missing.isEmpty() && !invalidationPlanned) {
      throw new InitializationConflictException(new IllegalStateException("权限关联在初始化预检后发生变化"));
    }
    for (int start = 0; start < missing.size(); start += BATCH_SIZE) {
      try {
        resourceApiMapper.insertBatch(
            missing.subList(start, Math.min(start + BATCH_SIZE, missing.size())));
      } catch (DuplicateKeyException exception) {
        throw translateConflict(exception, RESOURCE_API_INDEX);
      }
    }
  }

  private void ensureAdministrator(String username, String password, long departmentId) {
    EmployeeEntity existing = employeeMapper.selectByUsername(username);
    if (existing != null) {
      if (existing.getStatus() != SystemStatus.ENABLED.getCode() || existing.getSuperuser() != 1) {
        throw new BusinessException(ExceptionCode.INITIALIZATION_ADMIN_INVALID);
      }
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    EmployeeEntity employee = new EmployeeEntity();
    employee.setUsername(username);
    employee.setName("超级管理员");
    employee.setPasswordHash(passwordEncoder.encode(password));
    employee.setDepartmentId(departmentId);
    employee.setStatus(SystemStatus.ENABLED.getCode());
    employee.setSuperuser(1);
    employee.setCreatedAt(now);
    employee.setUpdatedAt(now);
    try {
      employeeMapper.insert(employee);
    } catch (DuplicateKeyException exception) {
      throw translateConflict(exception, EMPLOYEE_USERNAME_INDEX);
    }
  }

  private RuntimeException translateConflict(
      DuplicateKeyException exception, String... expectedIndexes) {
    String message = exception.getMostSpecificCause().getMessage();
    if (message == null) {
      message = exception.getMessage();
    }
    if (message != null) {
      for (String expectedIndex : expectedIndexes) {
        if (message.contains(expectedIndex)) {
          return new InitializationConflictException(exception);
        }
      }
    }
    return exception;
  }

  private String requireText(String value, ExceptionCode code) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(code);
    }
    return value.strip();
  }

  private String validateUsername(String value) {
    String username = requireText(value, ExceptionCode.INITIALIZATION_USERNAME_REQUIRED);
    if (username.length() > 64) {
      throw new BusinessException(ExceptionCode.INITIALIZATION_USERNAME_INVALID);
    }
    return username;
  }

  private String validatePassword(String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ExceptionCode.INITIALIZATION_PASSWORD_REQUIRED);
    }
    if (value.length() < 8 || value.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new BusinessException(ExceptionCode.INITIALIZATION_PASSWORD_INVALID);
    }
    return value;
  }

  /** 初始化唯一键并发竞争。 */
  public static class InitializationConflictException extends RuntimeException {
    public InitializationConflictException(Throwable cause) {
      super("系统初始化发生并发竞争", cause);
    }
  }

  private static List<ResourceDefinition> definitions() {
    List<ResourceDefinition> definitions = new ArrayList<>();
    definitions.add(directory("system", ROOT_RESOURCE_NAME));
    addMenu(
        definitions,
        "employee",
        "员工管理",
        "/system/employees",
        "/api/system/employees",
        List.of(
            "page",
            "detail",
            "role-ids",
            "create",
            "update",
            "status",
            "delete",
            "reset-password",
            "roles"));
    addMenu(
        definitions,
        "department",
        "部门管理",
        "/system/departments",
        "/api/system/departments",
        List.of("tree", "detail", "create", "update", "status", "delete"));
    addMenu(
        definitions,
        "role",
        "角色管理",
        "/system/roles",
        "/api/system/roles",
        List.of(
            "page", "detail", "resource-ids", "create", "update", "status", "delete", "resources"));
    addMenu(
        definitions,
        "resource",
        "资源管理",
        "/system/resources",
        "/api/system/resources",
        List.of("tree", "detail", "create", "update", "status", "delete", "api-ids", "apis"));
    addMenu(
        definitions,
        "api",
        "接口管理",
        "/system/apis",
        "/api/system/apis",
        List.of("page", "detail", "status"));
    return List.copyOf(definitions);
  }

  private static ResourceDefinition directory(String code, String name) {
    return new ResourceDefinition(code, name, ResourceType.DIRECTORY, null, null, null, 0);
  }

  private static void addMenu(
      List<ResourceDefinition> definitions,
      String module,
      String name,
      String menuPath,
      String apiPrefix,
      List<String> operations) {
    String menuCode = "system:" + module;
    definitions.add(
        new ResourceDefinition(
            menuCode, name, ResourceType.MENU, "system", menuPath, null, definitions.size()));
    for (String operation : operations) {
      definitions.add(
          new ResourceDefinition(
              menuCode + ":" + operation,
              name + operation,
              ResourceType.ACTION,
              menuCode,
              null,
              apiPrefix + "/" + operation,
              definitions.size()));
    }
  }

  private static final class ResourceDefinition {
    private final String code;
    private final String name;
    private final ResourceType type;
    private final String parentCode;
    private final String menuPath;
    private final String apiPath;
    private final int sortOrder;

    private ResourceDefinition(
        String code,
        String name,
        ResourceType type,
        String parentCode,
        String menuPath,
        String apiPath,
        int sortOrder) {
      this.code = code;
      this.name = name;
      this.type = type;
      this.parentCode = parentCode;
      this.menuPath = menuPath;
      this.apiPath = apiPath;
      this.sortOrder = sortOrder;
    }
  }
}
