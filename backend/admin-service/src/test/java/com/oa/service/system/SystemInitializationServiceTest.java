package com.oa.service.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class SystemInitializationServiceTest {
  private DepartmentMapper departmentMapper;
  private SystemResourceMapper resourceMapper;
  private SystemApiMapper apiMapper;
  private ResourceApiMapper resourceApiMapper;
  private EmployeeMapper employeeMapper;
  private PermissionService permissionService;
  private SystemInitializationService service;

  @BeforeEach
  void setUp() {
    departmentMapper = mock(DepartmentMapper.class);
    resourceMapper = mock(SystemResourceMapper.class);
    apiMapper = mock(SystemApiMapper.class);
    resourceApiMapper = mock(ResourceApiMapper.class);
    employeeMapper = mock(EmployeeMapper.class);
    permissionService = mock(PermissionService.class);
    service =
        new SystemInitializationService(
            departmentMapper,
            resourceMapper,
            apiMapper,
            resourceApiMapper,
            employeeMapper,
            permissionService,
            new BCryptPasswordEncoder());
  }

  @Test
  void 初始化方法使用单一事务() throws NoSuchMethodException {
    assertTrue(
        SystemInitializationService.class
            .getMethod("initialize", String.class, String.class)
            .isAnnotationPresent(Transactional.class));
  }

  @Test
  void 初始管理员密码不能为空且不访问数据库() {
    BusinessException exception =
        assertThrows(BusinessException.class, () -> service.initialize("admin", "  "));
    assertEquals(ExceptionCode.INITIALIZATION_PASSWORD_REQUIRED, exception.getExceptionCode());
    verify(departmentMapper, never()).selectByParentIdAndName(anyLong(), anyString());
  }

  @Test
  void 用户名超过六十四字符时在任何依赖交互前失败() {
    BusinessException exception =
        assertThrows(
            BusinessException.class, () -> service.initialize("a".repeat(65), "strong-password"));
    assertEquals(ExceptionCode.INITIALIZATION_USERNAME_INVALID, exception.getExceptionCode());
    verifyNoDependencyInteractions();
  }

  @Test
  void 密码少于八字符时在任何依赖交互前失败() {
    BusinessException exception =
        assertThrows(BusinessException.class, () -> service.initialize("admin", "1234567"));
    assertEquals(ExceptionCode.INITIALIZATION_PASSWORD_INVALID, exception.getExceptionCode());
    verifyNoDependencyInteractions();
  }

  @Test
  void 多字节密码超过七十二字节时在任何依赖交互前失败() {
    BusinessException exception =
        assertThrows(BusinessException.class, () -> service.initialize("admin", "密".repeat(25)));
    assertEquals(ExceptionCode.INITIALIZATION_PASSWORD_INVALID, exception.getExceptionCode());
    verifyNoDependencyInteractions();
  }

  @Test
  void 用户名和密码边界值通过前置校验() {
    when(apiMapper.selectByPath(anyString())).thenReturn(null);
    assertEquals(
        ExceptionCode.INITIALIZATION_API_MISSING,
        assertThrows(
                BusinessException.class, () -> service.initialize("a".repeat(64), "密".repeat(24)))
            .getExceptionCode());
    verify(apiMapper).selectByPath(anyString());

    org.mockito.Mockito.clearInvocations(apiMapper);
    assertEquals(
        ExceptionCode.INITIALIZATION_API_MISSING,
        assertThrows(BusinessException.class, () -> service.initialize("a", "12345678"))
            .getExceptionCode());
    verify(apiMapper).selectByPath(anyString());
  }

  @Test
  void 必需接口缺失时在写入前失败以保证事务回滚语义() {
    when(apiMapper.selectByPath(anyString())).thenReturn(null);
    BusinessException exception =
        assertThrows(BusinessException.class, () -> service.initialize("admin", "strong-password"));
    assertEquals(ExceptionCode.INITIALIZATION_API_MISSING, exception.getExceptionCode());
    verify(departmentMapper, never()).insert(any(DepartmentEntity.class));
    verify(resourceMapper, never()).insert(any(SystemResourceEntity.class));
    verify(employeeMapper, never()).insert(any(EmployeeEntity.class));
  }

  @Test
  void 已有根资源类型冲突时拒绝初始化并保留原数据() {
    when(apiMapper.selectByPath(anyString()))
        .thenAnswer(invocation -> api(invocation.getArgument(0)));
    SystemResourceEntity root = new SystemResourceEntity();
    root.setId(9L);
    root.setCode("system");
    root.setParentId(0L);
    root.setType("MENU");
    when(resourceMapper.selectByParentIdAndName(0L, "系统管理")).thenReturn(root);

    BusinessException exception =
        assertThrows(BusinessException.class, () -> service.initialize("admin", "strong-password"));

    assertEquals(ExceptionCode.INITIALIZATION_RESOURCE_CONFLICT, exception.getExceptionCode());
    verify(resourceMapper, never()).updateById(any(SystemResourceEntity.class));
  }

  @Test
  void 已有同名普通员工不能视为初始化成功() {
    prepareExistingStructure();
    EmployeeEntity employee = employee(1, 0);
    when(employeeMapper.selectByUsername("admin")).thenReturn(employee);

    BusinessException exception =
        assertThrows(BusinessException.class, () -> service.initialize("admin", "password"));

    assertEquals(ExceptionCode.INITIALIZATION_ADMIN_INVALID, exception.getExceptionCode());
    verify(employeeMapper, never()).updateById(any(EmployeeEntity.class));
  }

  @Test
  void 已有同名禁用超级管理员不能视为初始化成功() {
    prepareExistingStructure();
    when(employeeMapper.selectByUsername("admin")).thenReturn(employee(0, 1));

    BusinessException exception =
        assertThrows(BusinessException.class, () -> service.initialize("admin", "password"));

    assertEquals(ExceptionCode.INITIALIZATION_ADMIN_INVALID, exception.getExceptionCode());
  }

  @Test
  void 根部门唯一键竞争转换为统一初始化竞争异常() {
    prepareApis();
    when(departmentMapper.insert(any(DepartmentEntity.class)))
        .thenThrow(new DuplicateKeyException("udx_department_parent_name"));

    assertThrows(
        SystemInitializationService.InitializationConflictException.class,
        () -> service.initialize("admin", "password"));
  }

  @Test
  void 根资源编码唯一键竞争转换为统一初始化竞争异常() {
    prepareApis();
    DepartmentEntity department = new DepartmentEntity();
    department.setId(1L);
    when(departmentMapper.selectByParentIdAndName(0L, "总部")).thenReturn(department);
    when(resourceMapper.insert(any(SystemResourceEntity.class)))
        .thenThrow(new DuplicateKeyException("udx_system_resource_code"));

    assertThrows(
        SystemInitializationService.InitializationConflictException.class,
        () -> service.initialize("admin", "password"));
  }

  @Test
  void 未知重复键异常保持原样抛出() {
    prepareApis();
    DuplicateKeyException duplicate = new DuplicateKeyException("unknown_unique_index");
    when(departmentMapper.insert(any(DepartmentEntity.class))).thenThrow(duplicate);

    assertSame(
        duplicate,
        assertThrows(DuplicateKeyException.class, () -> service.initialize("admin", "password")));
  }

  @Test
  void 首次创建后第二次初始化完整复用且资源总数为四十() {
    AtomicReference<DepartmentEntity> department = new AtomicReference<>();
    AtomicReference<EmployeeEntity> employee = new AtomicReference<>();
    Map<String, SystemResourceEntity> resources = new HashMap<>();
    Map<Long, Set<Long>> relations = new HashMap<>();
    prepareCreationSimulation(department, employee, resources, relations);

    service.initialize("admin", "strong-password");
    service.initialize("admin", "strong-password");

    org.mockito.ArgumentCaptor<List<?>> batch = org.mockito.ArgumentCaptor.forClass(List.class);
    verify(resourceApiMapper, times(1)).insertBatch((List) batch.capture());
    verify(departmentMapper, times(1)).insert(any(DepartmentEntity.class));
    verify(resourceMapper, times(40)).insert(any(SystemResourceEntity.class));
    verify(employeeMapper, times(1)).insert(any(EmployeeEntity.class));
    verify(permissionService, times(1)).invalidateAll();
    InOrder order = inOrder(permissionService, departmentMapper, resourceMapper);
    order.verify(permissionService).invalidateAll();
    order.verify(departmentMapper).insert(any(DepartmentEntity.class));
    order.verify(resourceMapper, times(40)).insert(any(SystemResourceEntity.class));
    assertEquals(34, batch.getValue().size());
    assertTrue(batch.getValue().size() <= 500);
  }

  @Test
  void 已有资源补关联时先失效权限且只失效一次() {
    prepareExistingStructure();
    when(employeeMapper.selectByUsername("admin")).thenReturn(employee(1, 1));

    service.initialize("admin", "strong-password");

    InOrder order = inOrder(permissionService, resourceApiMapper);
    order.verify(permissionService).invalidateAll();
    order.verify(resourceApiMapper).insertBatch(org.mockito.ArgumentMatchers.anyList());
    verify(permissionService, times(1)).invalidateAll();
  }

  @Test
  void 预检无变化但写入阶段出现缺失关联时回滚重试而不直接插入() {
    AtomicReference<DepartmentEntity> department = new AtomicReference<>();
    AtomicReference<EmployeeEntity> employee = new AtomicReference<>();
    Map<String, SystemResourceEntity> resources = new HashMap<>();
    Map<Long, Set<Long>> relations = new HashMap<>();
    prepareCreationSimulation(department, employee, resources, relations);
    service.initialize("admin", "strong-password");
    org.mockito.Mockito.clearInvocations(permissionService, resourceApiMapper);
    AtomicLong reads = new AtomicLong();
    when(resourceApiMapper.selectApiIdsByResourceId(anyLong()))
        .thenAnswer(
            invocation -> {
              long resourceId = invocation.getArgument(0);
              if (reads.incrementAndGet() == 35) {
                return List.of();
              }
              return List.copyOf(relations.getOrDefault(resourceId, Set.of()));
            });

    assertThrows(
        SystemInitializationService.InitializationConflictException.class,
        () -> service.initialize("admin", "strong-password"));

    verify(permissionService, never()).invalidateAll();
    verify(resourceApiMapper, never()).insertBatch(org.mockito.ArgumentMatchers.anyList());
  }

  @Test
  void 管理员竞争使首事务回滚且第二次重试开启新事务() {
    AtomicReference<DepartmentEntity> department = new AtomicReference<>();
    AtomicReference<EmployeeEntity> employee = new AtomicReference<>();
    Map<String, SystemResourceEntity> resources = new HashMap<>();
    Map<Long, Set<Long>> relations = new HashMap<>();
    prepareCreationSimulation(department, employee, resources, relations);
    when(employeeMapper.selectByUsername("admin")).thenReturn(null, employee(1, 1));
    when(employeeMapper.insert(any(EmployeeEntity.class)))
        .thenThrow(new DuplicateKeyException("udx_employee_username"));
    CountingTransactionManager transactionManager = new CountingTransactionManager();
    ProxyFactory proxyFactory = new ProxyFactory(service);
    proxyFactory.setProxyTargetClass(true);
    proxyFactory.addAdvice(
        new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
    SystemInitializationService transactionalService =
        (SystemInitializationService) proxyFactory.getProxy();

    assertThrows(
        SystemInitializationService.InitializationConflictException.class,
        () -> transactionalService.initialize("admin", "strong-password"));
    transactionalService.initialize("admin", "strong-password");

    assertEquals(2, transactionManager.begins);
    assertEquals(1, transactionManager.rollbacks);
    assertEquals(1, transactionManager.commits);
    verify(resourceApiMapper, times(1)).insertBatch(org.mockito.ArgumentMatchers.anyList());
  }

  private SystemApiEntity api(String path) {
    SystemApiEntity api = new SystemApiEntity();
    api.setId(Integer.toUnsignedLong(path.hashCode()));
    api.setPath(path);
    return api;
  }

  private void verifyNoDependencyInteractions() {
    verifyNoInteractions(
        departmentMapper,
        resourceMapper,
        apiMapper,
        resourceApiMapper,
        employeeMapper,
        permissionService);
  }

  private void prepareApis() {
    when(apiMapper.selectByPath(anyString()))
        .thenAnswer(invocation -> api(invocation.getArgument(0)));
  }

  private void prepareExistingStructure() {
    prepareApis();
    DepartmentEntity department = new DepartmentEntity();
    department.setId(1L);
    when(departmentMapper.selectByParentIdAndName(0L, "总部")).thenReturn(department);
    when(resourceMapper.selectByParentIdAndName(0L, "系统管理")).thenReturn(resource("system"));
    when(resourceMapper.selectByCode(anyString()))
        .thenAnswer(invocation -> resource(invocation.getArgument(0)));
    when(resourceApiMapper.selectApiIdsByResourceId(anyLong())).thenAnswer(invocation -> List.of());
  }

  private EmployeeEntity employee(int status, int superuser) {
    EmployeeEntity employee = new EmployeeEntity();
    employee.setId(1L);
    employee.setStatus(status);
    employee.setSuperuser(superuser);
    return employee;
  }

  private void prepareCreationSimulation(
      AtomicReference<DepartmentEntity> department,
      AtomicReference<EmployeeEntity> employee,
      Map<String, SystemResourceEntity> resources,
      Map<Long, Set<Long>> relations) {
    prepareApis();
    AtomicLong resourceId = new AtomicLong(100L);
    when(departmentMapper.selectByParentIdAndName(0L, "总部"))
        .thenAnswer(invocation -> department.get());
    doAnswer(
            invocation -> {
              DepartmentEntity inserted = invocation.getArgument(0);
              inserted.setId(1L);
              department.set(inserted);
              return 1;
            })
        .when(departmentMapper)
        .insert(any(DepartmentEntity.class));
    when(resourceMapper.selectByParentIdAndName(0L, "系统管理"))
        .thenAnswer(invocation -> resources.get("system"));
    when(resourceMapper.selectByCode(anyString()))
        .thenAnswer(invocation -> resources.get(invocation.getArgument(0)));
    doAnswer(
            invocation -> {
              SystemResourceEntity inserted = invocation.getArgument(0);
              inserted.setId(resourceId.getAndIncrement());
              resources.put(inserted.getCode(), inserted);
              return 1;
            })
        .when(resourceMapper)
        .insert(any(SystemResourceEntity.class));
    when(resourceApiMapper.selectApiIdsByResourceId(anyLong()))
        .thenAnswer(
            invocation -> List.copyOf(relations.getOrDefault(invocation.getArgument(0), Set.of())));
    doAnswer(
            invocation -> {
              List<ResourceApiEntity> batch = invocation.getArgument(0);
              for (ResourceApiEntity relation : batch) {
                relations
                    .computeIfAbsent(relation.getResourceId(), ignored -> new HashSet<>())
                    .add(relation.getApiId());
              }
              return batch.size();
            })
        .when(resourceApiMapper)
        .insertBatch(org.mockito.ArgumentMatchers.anyList());
    when(employeeMapper.selectByUsername("admin")).thenAnswer(invocation -> employee.get());
    doAnswer(
            invocation -> {
              EmployeeEntity inserted = invocation.getArgument(0);
              inserted.setId(2L);
              employee.set(inserted);
              return 1;
            })
        .when(employeeMapper)
        .insert(any(EmployeeEntity.class));
  }

  private SystemResourceEntity resource(String code) {
    SystemResourceEntity resource = new SystemResourceEntity();
    resource.setId(Integer.toUnsignedLong(code.hashCode()));
    resource.setCode(code);
    resource.setType(
        code.equals("system") ? "DIRECTORY" : code.split(":").length == 2 ? "MENU" : "ACTION");
    if (!code.equals("system")) {
      String parentCode =
          code.split(":").length == 2 ? "system" : code.substring(0, code.lastIndexOf(':'));
      resource.setParentId(Integer.toUnsignedLong(parentCode.hashCode()));
    }
    return resource;
  }

  private static final class CountingTransactionManager extends AbstractPlatformTransactionManager {
    private int begins;
    private int commits;
    private int rollbacks;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      begins++;
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      commits++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      rollbacks++;
    }
  }
}
