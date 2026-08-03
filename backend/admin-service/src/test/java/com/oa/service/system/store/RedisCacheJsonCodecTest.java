package com.oa.service.system.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.model.system.cache.EmployeeAuthorizationCache;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Redis 缓存 JSON 编解码测试。 */
class RedisCacheJsonCodecTest {

  /** 员工授权关系缓存必须能够完整往返。 */
  @Test
  void 员工授权关系缓存完整往返() {
    RedisCacheJsonCodec codec = new RedisCacheJsonCodec(new ObjectMapper());
    EmployeeAuthorizationCache source = employeeAuthorizationCache();

    String json = codec.write(source);
    EmployeeAuthorizationCache result = codec.read(json, EmployeeAuthorizationCache.class);

    assertEquals(7L, result.getEmployeeId());
    assertEquals(1, result.getStatus());
    assertEquals(true, result.getSuperuser());
    assertEquals(List.of(10L, 20L), result.getRoleIds());
  }

  /** 非法 JSON 必须统一包装为 Codec 异常。 */
  @Test
  void 非法Json统一包装转换异常() {
    RedisCacheJsonCodec codec = new RedisCacheJsonCodec(new ObjectMapper());

    assertThrows(
        RedisCacheJsonException.class,
        () -> codec.read("not-json", EmployeeAuthorizationCache.class));
  }

  private EmployeeAuthorizationCache employeeAuthorizationCache() {
    EmployeeAuthorizationCache cache = new EmployeeAuthorizationCache();
    cache.setEmployeeId(7L);
    cache.setStatus(1);
    cache.setSuperuser(true);
    cache.setRoleIds(List.of(10L, 20L));
    return cache;
  }
}
