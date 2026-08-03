package com.oa.service.system.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.system.cache.EmployeeAuthorizationCache;
import com.oa.common.model.system.cache.RoleAuthorizationCache;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

/** Redis员工与角色授权缓存存储测试。 */
class StringRedisAuthorizationStoreTest {
  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> valueOperations;
  private StringRedisAuthorizationStore store;

  @BeforeEach
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    store =
        new StringRedisAuthorizationStore(
            redisTemplate, new RedisCacheJsonCodec(new ObjectMapper()));
  }

  /** 员工授权缓存必须使用固定Key和十分钟TTL。 */
  @Test
  void 员工授权缓存按员工读写删除() {
    EmployeeAuthorizationCache cache = employeeCache();
    when(valueOperations.get("admin:employee-auth:7"))
        .thenReturn("{\"employeeId\":7,\"status\":1,\"superuser\":false,\"roleIds\":[10,20]}");

    EmployeeAuthorizationCache result = store.getEmployee(7L);
    store.putEmployee(cache);
    store.deleteEmployee(7L);

    assertEquals(List.of(10L, 20L), result.getRoleIds());
    verify(valueOperations)
        .set(
            eq("admin:employee-auth:7"),
            eq("{\"employeeId\":7,\"status\":1,\"superuser\":false,\"roleIds\":[10,20]}"),
            eq(Duration.ofMinutes(10)));
    verify(redisTemplate).delete("admin:employee-auth:7");
  }

  /** 角色批量读取必须保持角色ID与返回位置对应，并忽略缓存缺失。 */
  @Test
  void 角色授权缓存使用Mget批量读取() {
    when(valueOperations.multiGet(
            List.of("admin:role-auth:10", "admin:role-auth:20", "admin:role-auth:30")))
        .thenReturn(
            java.util.Arrays.asList(
                "{\"roleId\":10,\"status\":1,\"resourceIds\":[100]}",
                null,
                "{\"roleId\":30,\"status\":0,\"resourceIds\":[]}"));

    Map<Long, RoleAuthorizationCache> result = store.multiGetRoles(List.of(10L, 20L, 30L));

    assertEquals(List.of(10L, 30L), result.keySet().stream().toList());
    assertEquals(List.of(100L), result.get(10L).getResourceIds());
    assertEquals(0, result.get(30L).getStatus());
  }

  /** 角色缓存回填和删除必须使用角色Key与十分钟TTL。 */
  @Test
  void 角色授权缓存逐个回填并支持精准删除() {
    RoleAuthorizationCache cache = roleCache(10L, 1, List.of(100L, 101L));

    store.putRoles(List.of(cache));
    store.deleteRole(10L);

    verify(valueOperations)
        .set(
            "admin:role-auth:10",
            "{\"roleId\":10,\"status\":1,\"resourceIds\":[100,101]}",
            Duration.ofMinutes(10));
    verify(redisTemplate).delete("admin:role-auth:10");
  }

  /** 快照版本镜像只能单调推进。 */
  @Test
  void 快照版本使用单键Lua单调推进() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("8"))).thenReturn(8L);

    assertEquals(8L, store.advanceSnapshotVersion(8L));

    ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
    verify(redisTemplate)
        .execute(script.capture(), eq(List.of("admin:permission:snapshot:version")), eq("8"));
    assertTrue(script.getValue().getScriptAsString().contains("candidate > current"));
  }

  /** Redis和缓存JSON异常必须统一转换为认证基础设施异常。 */
  @Test
  void Redis和Json异常统一转换() {
    when(valueOperations.get("admin:employee-auth:7"))
        .thenThrow(new DataAccessResourceFailureException("down"));
    assertThrows(AuthenticationInfrastructureException.class, () -> store.getEmployee(7L));

    when(valueOperations.get("admin:employee-auth:8")).thenReturn("not-json");
    assertThrows(AuthenticationInfrastructureException.class, () -> store.getEmployee(8L));
  }

  private EmployeeAuthorizationCache employeeCache() {
    EmployeeAuthorizationCache cache = new EmployeeAuthorizationCache();
    cache.setEmployeeId(7L);
    cache.setStatus(1);
    cache.setSuperuser(false);
    cache.setRoleIds(List.of(10L, 20L));
    return cache;
  }

  private RoleAuthorizationCache roleCache(long roleId, int status, List<Long> resourceIds) {
    RoleAuthorizationCache cache = new RoleAuthorizationCache();
    cache.setRoleId(roleId);
    cache.setStatus(status);
    cache.setResourceIds(resourceIds);
    return cache;
  }
}
