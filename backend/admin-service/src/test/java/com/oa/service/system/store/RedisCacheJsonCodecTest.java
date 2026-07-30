package com.oa.service.system.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.model.system.cache.EmployeePermissionCache;
import com.oa.common.model.system.enums.ResourceType;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.model.system.vo.ResourceVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Redis 缓存 JSON 编解码测试。 */
class RedisCacheJsonCodecTest {

  /** 权限缓存中的多层资源、枚举和日期时间必须能够完整往返。 */
  @Test
  void 权限缓存复杂字段完整往返() {
    RedisCacheJsonCodec codec = new RedisCacheJsonCodec(new ObjectMapper());
    EmployeePermissionCache source = permissionCache();

    String json = codec.write(source);
    EmployeePermissionCache result = codec.read(json, EmployeePermissionCache.class);

    assertFalse(json.contains("[2026,7,30"));
    assertEquals(8L, result.getVersion());
    assertEquals(Set.of("/api/system/resource/tree"), result.getApiPaths());
    ResourceVO directory = result.getResources().get(0);
    assertEquals(ResourceType.DIRECTORY, directory.getType());
    assertEquals(SystemStatus.ENABLED, directory.getStatus());
    assertEquals(LocalDateTime.of(2026, 7, 30, 9, 10, 11, 123_000_000), directory.getCreatedAt());
    ResourceVO menu = directory.getChildren().get(0);
    assertEquals(ResourceType.MENU, menu.getType());
    assertEquals(LocalDateTime.of(2026, 7, 30, 10, 20, 30, 456_000_000), menu.getUpdatedAt());
  }

  /** 非法 JSON 必须统一包装为 Codec 异常。 */
  @Test
  void 非法Json统一包装转换异常() {
    RedisCacheJsonCodec codec = new RedisCacheJsonCodec(new ObjectMapper());

    assertThrows(
        RedisCacheJsonException.class, () -> codec.read("not-json", EmployeePermissionCache.class));
  }

  private EmployeePermissionCache permissionCache() {
    ResourceVO menu = resource(2L, 1L, ResourceType.MENU, "员工管理");
    menu.setUpdatedAt(LocalDateTime.of(2026, 7, 30, 10, 20, 30, 456_000_000));
    ResourceVO directory = resource(1L, 0L, ResourceType.DIRECTORY, "系统管理");
    directory.setCreatedAt(LocalDateTime.of(2026, 7, 30, 9, 10, 11, 123_000_000));
    directory.setChildren(List.of(menu));

    EmployeePermissionCache cache = new EmployeePermissionCache();
    cache.setVersion(8L);
    cache.setApiPaths(Set.of("/api/system/resource/tree"));
    cache.setResources(List.of(directory));
    return cache;
  }

  private ResourceVO resource(long id, long parentId, ResourceType type, String name) {
    ResourceVO resource = new ResourceVO();
    resource.setId(id);
    resource.setParentId(parentId);
    resource.setType(type);
    resource.setName(name);
    resource.setCode("RESOURCE_" + id);
    resource.setPath("/resource/" + id);
    resource.setIcon("menu");
    resource.setSortOrder((int) id);
    resource.setVisible(true);
    resource.setStatus(SystemStatus.ENABLED);
    resource.setChildren(List.of());
    return resource;
  }
}
