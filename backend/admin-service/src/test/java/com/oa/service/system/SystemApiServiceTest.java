package com.oa.service.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oa.common.exception.BusinessException;
import com.oa.common.model.common.enums.ExceptionCode;
import com.oa.common.model.system.dto.SystemApiDefinitionDTO;
import com.oa.dao.system.SystemApiMapper;
import com.oa.entity.system.SystemApiEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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

    assertEquals("新名称", disabledExisting.getName());
    assertEquals("新描述", disabledExisting.getDescription());
    assertEquals(0, disabledExisting.getStatus());
    assertNotNull(disabledExisting.getUpdatedAt());
    verify(systemApiMapper).updateById(disabledExisting);

    assertEquals(0, removed.getStatus());
    assertNotNull(removed.getUpdatedAt());
    verify(systemApiMapper).updateById(removed);
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
    verify(systemApiMapper, never()).updateById(any(SystemApiEntity.class));
    verify(permissionService, never()).invalidateAll();
    verify(permissionService).retryPendingInvalidation();
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
