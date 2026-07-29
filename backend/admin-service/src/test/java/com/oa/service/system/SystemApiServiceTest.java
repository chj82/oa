package com.oa.service.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.SystemApiDefinitionDTO;
import com.oa.common.model.system.dto.SystemApiQueryDTO;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.model.system.vo.SystemApiVO;
import com.oa.common.response.PageResult;
import com.oa.dao.system.SystemApiMapper;
import com.oa.entity.system.SystemApiEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;

/** 系统接口同步服务测试。 */
class SystemApiServiceTest {
  @Mock private SystemApiMapper systemApiMapper;
  @Mock private PermissionService permissionService;
  private SystemApiService systemApiService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    systemApiService = new SystemApiService(systemApiMapper, permissionService);
  }

  /** 新路径新增，已有路径更新名称描述并保留人工禁用状态，移除路径只禁用。 */
  @Test
  void 同步接口目录且不修改资源关联() {
    SystemApiEntity disabledExisting = api(1L, "旧名称", "/api/existing", "旧描述", 0);
    SystemApiEntity removed = api(2L, "已移除", "/api/removed", "旧接口", 1);
    when(systemApiMapper.selectAll()).thenReturn(List.of(disabledExisting, removed));

    systemApiService.sync(
        List.of(definition("新名称", "/api/existing", "新描述"), definition("新增接口", "/api/new", "新增描述")));

    ArgumentCaptor<SystemApiEntity> insertCaptor = ArgumentCaptor.forClass(SystemApiEntity.class);
    verify(systemApiMapper).insert(insertCaptor.capture());
    SystemApiEntity inserted = insertCaptor.getValue();
    assertEquals("新增接口", inserted.getName());
    assertEquals("/api/new", inserted.getPath());
    assertEquals("新增描述", inserted.getDescription());
    assertEquals(1, inserted.getStatus());
    assertNotNull(inserted.getCreatedAt());
    assertNotNull(inserted.getUpdatedAt());

    assertEquals(0, disabledExisting.getStatus());
    verify(systemApiMapper).updateMetadata(eq(1L), eq("新名称"), eq("新描述"), any(LocalDateTime.class));

    verify(systemApiMapper).updateStatusIfCurrent(eq(2L), eq(1), eq(0), any(LocalDateTime.class));
    verify(permissionService).invalidateAll();
  }

  /** 不同 Handler 即使 HTTP Method 不同，只要路由模板相同就必须失败。 */
  @Test
  void 重复路径在访问数据库前失败() {
    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () ->
                systemApiService.sync(
                    List.of(
                        definition("查询", "/api/employees", "GET"),
                        definition("新增", "/api/employees", "POST"))));

    assertEquals(ExceptionCode.SYSTEM_API_PATH_DUPLICATED, exception.getExceptionCode());
    verify(systemApiMapper, never()).selectAll();
    verify(systemApiMapper, never()).insert(any(SystemApiEntity.class));
    verify(permissionService, never()).invalidateAll();
  }

  /** 接口目录没有实际变化时不递增权限缓存版本。 */
  @Test
  void 无变化不失效权限缓存() {
    SystemApiEntity existing = api(7L, "查询订单", "/api/orders", "查询订单", 1);
    when(systemApiMapper.selectAll()).thenReturn(List.of(existing));

    systemApiService.sync(List.of(definition("查询订单", "/api/orders", "查询订单")));

    verify(systemApiMapper, never()).insert(any(SystemApiEntity.class));
    verify(systemApiMapper, never())
        .updateMetadata(anyLong(), any(String.class), any(String.class), any(LocalDateTime.class));
    verify(permissionService, never()).invalidateAll();
    verify(permissionService).retryPendingInvalidation();
  }

  @Test
  void 分页查询并返回时间字段() {
    SystemApiQueryDTO query = new SystemApiQueryDTO();
    query.setPage(2);
    query.setSize(10);
    SystemApiEntity entity = api(8L, "查询接口", "/api/query", "查询", 1);
    Page<SystemApiEntity> page = new Page<>(2, 10, 21);
    page.setRecords(List.of(entity));
    when(systemApiMapper.selectSystemApiPage(query)).thenReturn(page);

    PageResult<SystemApiVO> result = systemApiService.page(query);

    assertEquals(21, result.getTotal());
    assertEquals(2, result.getPage());
    assertEquals(10, result.getSize());
    assertEquals(entity.getCreatedAt(), result.getRecords().get(0).getCreatedAt());
    assertEquals(entity.getUpdatedAt(), result.getRecords().get(0).getUpdatedAt());
  }

  @Test
  void 详情不存在使用数字异常码() {
    when(systemApiMapper.selectById(99L)).thenReturn(null);

    BusinessException exception =
        assertThrows(BusinessException.class, () -> systemApiService.detail(99L));

    assertEquals(ExceptionCode.SYSTEM_API_NOT_FOUND, exception.getExceptionCode());
  }

  @Test
  void 相同状态不失效权限也不更新() {
    when(systemApiMapper.selectById(3L)).thenReturn(api(3L, "接口", "/api/a", null, 1));

    systemApiService.changeStatus(3L, SystemStatus.ENABLED);

    verify(permissionService, never()).invalidateAll();
    verify(systemApiMapper, never())
        .updateStatusIfCurrent(anyLong(), anyInt(), anyInt(), any(LocalDateTime.class));
  }

  @Test
  void 状态变化先失效权限再条件更新() {
    when(systemApiMapper.selectById(3L)).thenReturn(api(3L, "接口", "/api/a", null, 1));
    when(systemApiMapper.updateStatusIfCurrent(eq(3L), eq(1), eq(0), any(LocalDateTime.class)))
        .thenReturn(1);

    systemApiService.changeStatus(3L, SystemStatus.DISABLED);

    InOrder order = inOrder(permissionService, systemApiMapper);
    order.verify(permissionService).invalidateAll();
    order
        .verify(systemApiMapper)
        .updateStatusIfCurrent(eq(3L), eq(1), eq(0), any(LocalDateTime.class));
  }

  @Test
  void 状态条件更新失败报告并发冲突() {
    when(systemApiMapper.selectById(3L)).thenReturn(api(3L, "接口", "/api/a", null, 1));
    when(systemApiMapper.updateStatusIfCurrent(eq(3L), eq(1), eq(0), any(LocalDateTime.class)))
        .thenReturn(0);

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> systemApiService.changeStatus(3L, SystemStatus.DISABLED));

    assertEquals(ExceptionCode.SYSTEM_API_CONCURRENT_MODIFICATION, exception.getExceptionCode());
  }

  @Test
  void 仅元数据变化不失效权限且不写状态() {
    SystemApiEntity existing = api(1L, "旧名称", "/api/a", "旧描述", 0);
    when(systemApiMapper.selectAll()).thenReturn(List.of(existing));

    systemApiService.sync(List.of(definition("新名称", "/api/a", "新描述")));

    verify(systemApiMapper).updateMetadata(eq(1L), eq("新名称"), eq("新描述"), any(LocalDateTime.class));
    verify(permissionService, never()).invalidateAll();
  }

  @Test
  void 新增接口先失效权限再写数据库() {
    when(systemApiMapper.selectAll()).thenReturn(List.of());

    systemApiService.sync(List.of(definition("新增", "/api/new", null)));

    InOrder order = inOrder(permissionService, systemApiMapper);
    order.verify(permissionService).invalidateAll();
    order.verify(systemApiMapper).insert(any(SystemApiEntity.class));
  }

  @Test
  void 已人工禁用接口不会被同步重新启用() {
    SystemApiEntity existing = api(1L, "接口", "/api/a", null, 0);
    when(systemApiMapper.selectAll()).thenReturn(List.of(existing));

    systemApiService.sync(List.of(definition("接口", "/api/a", null)));

    verify(systemApiMapper, never())
        .updateStatusIfCurrent(anyLong(), anyInt(), anyInt(), any(LocalDateTime.class));
    verify(permissionService).retryPendingInvalidation();
  }

  @Test
  void 移除启用接口先失效再条件禁用() {
    when(systemApiMapper.selectAll()).thenReturn(List.of(api(2L, "移除", "/api/removed", null, 1)));
    when(systemApiMapper.updateStatusIfCurrent(eq(2L), eq(1), eq(0), any(LocalDateTime.class)))
        .thenReturn(1);

    systemApiService.sync(List.of());

    InOrder order = inOrder(permissionService, systemApiMapper);
    order.verify(permissionService).invalidateAll();
    order
        .verify(systemApiMapper)
        .updateStatusIfCurrent(eq(2L), eq(1), eq(0), any(LocalDateTime.class));
  }

  @Test
  void 插入唯一索引冲突转换为路径重复() {
    when(systemApiMapper.selectAll()).thenReturn(List.of());
    when(systemApiMapper.insert(any(SystemApiEntity.class)))
        .thenThrow(new DuplicateKeyException("udx_system_api_path"));

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> systemApiService.sync(List.of(definition("新增", "/api/new", null))));

    assertEquals(ExceptionCode.SYSTEM_API_PATH_DUPLICATED, exception.getExceptionCode());
  }

  private SystemApiDefinitionDTO definition(String name, String path, String description) {
    SystemApiDefinitionDTO definition = new SystemApiDefinitionDTO();
    definition.setName(name);
    definition.setPath(path);
    definition.setDescription(description);
    return definition;
  }

  private SystemApiEntity api(long id, String name, String path, String description, int status) {
    SystemApiEntity api = new SystemApiEntity();
    api.setId(id);
    api.setName(name);
    api.setPath(path);
    api.setDescription(description);
    api.setStatus(status);
    api.setCreatedAt(LocalDateTime.now().minusDays(1));
    api.setUpdatedAt(LocalDateTime.now().minusDays(1));
    return api;
  }
}
