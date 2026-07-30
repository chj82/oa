package com.oa.service.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** 系统接口目录管理服务测试。 */
class SystemApiServiceTest {
  @Mock private SystemApiMapper systemApiMapper;
  @Mock private PermissionService permissionService;
  private SystemApiService systemApiService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    systemApiService = new SystemApiService(systemApiMapper, permissionService);
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
