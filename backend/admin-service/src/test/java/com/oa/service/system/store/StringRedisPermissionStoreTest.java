package com.oa.service.system.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.system.cache.EmployeePermissionCache;
import com.oa.common.model.system.enums.ResourceType;
import com.oa.common.model.system.enums.SystemStatus;
import com.oa.common.model.system.vo.ResourceVO;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

/** Redis 员工权限缓存存储测试。 */
class StringRedisPermissionStoreTest {
  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> valueOperations;
  private StringRedisPermissionStore store;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    store =
        new StringRedisPermissionStore(redisTemplate, new RedisCacheJsonCodec(new ObjectMapper()));
  }

  /** 单次 Lua 同时读取全局版本和员工缓存，版本匹配才返回缓存。 */
  @Test
  void 读取当前版本缓存使用单次Lua() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class)))
        .thenReturn("{\"version\":3,\"apiPaths\":[\"/api/orders\"],\"resources\":[]}");

    EmployeePermissionCache cache = store.getIfCurrent(7L);

    assertEquals(3L, cache.getVersion());
    assertTrue(cache.getApiPaths().contains("/api/orders"));
    ArgumentCaptor<RedisScript<String>> script = ArgumentCaptor.forClass(RedisScript.class);
    verify(redisTemplate)
        .execute(
            script.capture(),
            eq(
                List.of(
                    "admin:permission:version",
                    "admin:permission:7",
                    "admin:permission:invalidating:pending",
                    "admin:permission:invalidating:lease:")));
    assertTrue(script.getValue().getScriptAsString().contains("cache.version"));
  }

  /** Lua 返回空表示缓存缺失或版本不一致。 */
  @Test
  void 版本不一致返回空() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class)))
        .thenReturn("__PERMISSION_CACHE_MISS__");

    assertNull(store.getIfCurrent(7L));
  }

  /** Lua 返回空属于执行结果异常，必须失败关闭而不能当作普通缓存未命中。 */
  @Test
  void Lua返回空时失败关闭() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class))).thenReturn(null);

    assertThrows(AuthenticationInfrastructureException.class, () -> store.getIfCurrent(7L));
  }

  /** 全局失效标记存在时，权限缓存读取必须失败关闭。 */
  @Test
  void 权限失效处理中拒绝读取旧缓存() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class)))
        .thenReturn("__PERMISSION_INVALIDATING__");

    assertThrows(AuthenticationInfrastructureException.class, () -> store.getIfCurrent(7L));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
    verify(redisTemplate).execute(any(RedisScript.class), keys.capture());
    assertEquals(
        List.of(
            "admin:permission:version",
            "admin:permission:7",
            "admin:permission:invalidating:pending",
            "admin:permission:invalidating:lease:"),
        keys.getValue());
  }

  /** 仅在版本仍匹配且无活动失效事务时原子写入一天TTL权限缓存。 */
  @Test
  void 权限缓存按当前版本原子写入() {
    EmployeePermissionCache cache = new EmployeePermissionCache();
    cache.setVersion(3L);
    cache.setApiPaths(Set.of("/api/orders"));
    cache.setResources(List.of());
    when(redisTemplate.execute(
            any(RedisScript.class),
            any(List.class),
            any(String.class),
            any(String.class),
            any(String.class)))
        .thenReturn(1L);

    assertTrue(store.putIfCurrent(7L, cache));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
    verify(redisTemplate)
        .execute(
            script.capture(),
            eq(
                List.of(
                    "admin:permission:version",
                    "admin:permission:7",
                    "admin:permission:invalidating:pending",
                    "admin:permission:invalidating:lease:")),
            eq("3"),
            org.mockito.ArgumentMatchers.contains("\"version\":3"),
            eq(Long.toString(Duration.ofDays(1).toMillis())));
    assertTrue(script.getValue().getScriptAsString().contains("cacheVersion"));
  }

  @Test
  void 权限版本变化时拒绝写入重建缓存() {
    EmployeePermissionCache cache = new EmployeePermissionCache();
    cache.setVersion(3L);
    cache.setApiPaths(Set.of("/api/orders"));
    cache.setResources(List.of());
    when(redisTemplate.execute(
            any(RedisScript.class),
            any(List.class),
            any(String.class),
            any(String.class),
            any(String.class)))
        .thenReturn(0L);

    assertFalse(store.putIfCurrent(7L, cache));
  }

  /** 实际写入 Redis 的权限 JSON 必须能够完整恢复资源树和日期时间。 */
  @Test
  void 权限缓存通过Redis字符串完整往返() {
    EmployeePermissionCache source = permissionCacheWithResourceTree();
    when(redisTemplate.execute(
            any(RedisScript.class),
            any(List.class),
            any(String.class),
            any(String.class),
            any(String.class)))
        .thenReturn(1L);

    assertTrue(store.putIfCurrent(7L, source));

    ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    verify(redisTemplate)
        .execute(
            any(RedisScript.class),
            any(List.class),
            eq("3"),
            json.capture(),
            eq(Long.toString(Duration.ofDays(1).toMillis())));
    reset(redisTemplate);
    when(redisTemplate.execute(any(RedisScript.class), any(List.class)))
        .thenReturn(json.getValue());

    EmployeePermissionCache result = store.getIfCurrent(7L);

    ResourceVO directory = result.getResources().get(0);
    assertEquals(ResourceType.DIRECTORY, directory.getType());
    assertEquals(SystemStatus.ENABLED, directory.getStatus());
    assertEquals(LocalDateTime.of(2026, 7, 30, 9, 10, 11), directory.getCreatedAt());
    assertEquals(ResourceType.MENU, directory.getChildren().get(0).getType());
  }

  /** 全局版本不存在时原子初始化为零，失效时通过 INCR 递增。 */
  @Test
  void 全局版本初始化并原子递增() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class))).thenReturn(0L);
    when(valueOperations.increment("admin:permission:version")).thenReturn(1L);

    assertEquals(0L, store.currentVersion());
    assertEquals(1L, store.incrementVersion());

    verify(valueOperations).increment("admin:permission:version");
  }

  /** 全局版本和原子递增返回空时必须按认证基础设施故障处理。 */
  @Test
  void 版本操作返回空时失败关闭() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class))).thenReturn(null);
    when(valueOperations.increment("admin:permission:version")).thenReturn(null);

    assertThrows(AuthenticationInfrastructureException.class, store::currentVersion);
    assertThrows(AuthenticationInfrastructureException.class, store::incrementVersion);
  }

  /** 合法 JSON 缺少权限路径字段时不得进入业务判断。 */
  @Test
  void 缓存Json缺少权限路径时失败关闭() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class)))
        .thenReturn("{\"version\":3}");

    assertThrows(AuthenticationInfrastructureException.class, () -> store.getIfCurrent(7L));
  }

  /** 合法 JSON 缺少版本字段时不得使用 Java 基本类型默认值冒充有效版本。 */
  @Test
  void 缓存Json缺少版本时失败关闭() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class)))
        .thenReturn("{\"apiPaths\":[]}");

    assertThrows(AuthenticationInfrastructureException.class, () -> store.getIfCurrent(7L));
  }

  /** 失效开始、提交和残留恢复都使用原子 Lua，提交同时递增版本并清理标记。 */
  @Test
  void 权限失效标记使用原子Lua维护() {
    when(redisTemplate.execute(
            any(RedisScript.class), any(List.class), any(String.class), any(String.class)))
        .thenReturn("token-1");
    when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("token-1")))
        .thenReturn(4L);
    when(redisTemplate.execute(any(RedisScript.class), any(List.class))).thenReturn(5L);

    String token = store.beginInvalidation();
    assertNotNull(token);
    assertEquals(4L, store.completeInvalidation(token));
    assertEquals(5L, store.retryPendingInvalidation());
  }

  /** 开始失效必须原子写入永久标记和60秒同token租约。 */
  @Test
  void 权限失效开始同时写入租约() {
    when(redisTemplate.execute(
            any(RedisScript.class), any(List.class), any(String.class), any(String.class)))
        .thenReturn("token-1");

    store.beginInvalidation();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<RedisScript<String>> script = ArgumentCaptor.forClass(RedisScript.class);
    verify(redisTemplate)
        .execute(
            script.capture(),
            eq(
                List.of(
                    "admin:permission:version",
                    "admin:permission:invalidating:pending",
                    "admin:permission:invalidating:lease:")),
            any(String.class),
            eq("60000"));
    assertTrue(script.getValue().getScriptAsString().contains("SADD', KEYS[2]"));
    assertTrue(script.getValue().getScriptAsString().contains("'PX', ARGV[2]"));
  }

  /** 活动租约存在时必须保持失败关闭，不能自动恢复标记。 */
  @Test
  void 活动租约存在时不自动恢复() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class)))
        .thenReturn("__PERMISSION_INVALIDATING__");

    assertThrows(AuthenticationInfrastructureException.class, () -> store.getIfCurrent(7L));

    ArgumentCaptor<RedisScript<String>> script = ArgumentCaptor.forClass(RedisScript.class);
    verify(redisTemplate).execute(script.capture(), any(List.class));
    assertTrue(script.getValue().getScriptAsString().contains("SMEMBERS', KEYS[3]"));
    assertTrue(script.getValue().getScriptAsString().contains("EXISTS', KEYS[4]"));
  }

  /** 租约过期后读取应由Lua递增版本、清标记并按缓存缺失重建。 */
  @Test
  void 过期租约自动失效旧版本并返回缓存缺失() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class)))
        .thenReturn("__PERMISSION_CACHE_MISS__");

    assertNull(store.getIfCurrent(7L));

    ArgumentCaptor<RedisScript<String>> script = ArgumentCaptor.forClass(RedisScript.class);
    verify(redisTemplate).execute(script.capture(), any(List.class));
    assertTrue(script.getValue().getScriptAsString().contains("INCR', KEYS[1]"));
    assertTrue(script.getValue().getScriptAsString().contains("SREM', KEYS[3]"));
  }

  /** 提交完成Redis失败后，租约过期仍可由后续读取安全恢复。 */
  @Test
  void 完成失效失败后可由过期租约恢复() {
    DataAccessResourceFailureException cause = new DataAccessResourceFailureException("连接失败");
    when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("token-1")))
        .thenThrow(cause);
    assertThrows(
        AuthenticationInfrastructureException.class, () -> store.completeInvalidation("token-1"));

    reset(redisTemplate);
    when(redisTemplate.execute(any(RedisScript.class), any(List.class)))
        .thenReturn("__PERMISSION_CACHE_MISS__");
    assertNull(store.getIfCurrent(7L));
  }

  /** 回滚只允许清理当前事务持有的失效 token。 */
  @Test
  void 回滚按Token清理失效标记() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("token-1")))
        .thenReturn(1L);

    store.cancelInvalidation("token-1");

    verify(redisTemplate).execute(any(RedisScript.class), any(List.class), eq("token-1"));
  }

  /** 并发事务完成或回滚只能移除自己的token，不能删除整个pending集合。 */
  @Test
  void 并发失效事务按Token独立清理() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("token-a")))
        .thenReturn(4L, 1L);

    store.completeInvalidation("token-a");
    store.cancelInvalidation("token-a");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<RedisScript<Long>> scripts = ArgumentCaptor.forClass(RedisScript.class);
    verify(redisTemplate, times(2)).execute(scripts.capture(), any(List.class), eq("token-a"));
    String complete = scripts.getAllValues().get(0).getScriptAsString();
    String cancel = scripts.getAllValues().get(1).getScriptAsString();
    assertTrue(complete.contains("SREM', KEYS[2], ARGV[1]"));
    assertTrue(complete.contains("KEYS[3] .. ARGV[1]"));
    assertTrue(cancel.contains("SREM', KEYS[1], ARGV[1]"));
    assertTrue(cancel.contains("KEYS[2] .. ARGV[1]"));
    assertFalse(complete.contains("DEL', KEYS[2])"));
    assertFalse(cancel.contains("DEL', KEYS[1])"));
  }

  /** 显式重试只恢复租约已过期token，不得清理仍有活动租约的token。 */
  @Test
  void 重试只清理过期Token并保留活动Token() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class))).thenReturn(5L);

    store.retryPendingInvalidation();

    ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
    verify(redisTemplate).execute(script.capture(), any(List.class));
    String source = script.getValue().getScriptAsString();
    assertTrue(source.contains("SMEMBERS', KEYS[2]"));
    assertTrue(source.contains("EXISTS', KEYS[3] .. token"));
    assertTrue(source.contains("SREM', KEYS[2], token"));
    assertTrue(source.contains("if recovered then redis.call('INCR'"));
    assertFalse(source.contains("DEL', KEYS[2]"));
  }

  /** 员工重建锁使用 SET NX 和短 TTL，释放时由 Lua 校验持有 token。 */
  @Test
  void 员工权限重建锁按Token获取和释放() {
    when(valueOperations.setIfAbsent(
            eq("admin:permission:rebuild-lock:7"), any(String.class), eq(Duration.ofSeconds(10))))
        .thenReturn(true);
    when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(String.class)))
        .thenReturn(1L);

    String token = store.tryAcquireRebuildLock(7L);
    assertNotNull(token);
    store.releaseRebuildLock(7L, token);

    verify(redisTemplate)
        .execute(any(RedisScript.class), eq(List.of("admin:permission:rebuild-lock:7")), eq(token));
  }

  /** Redis 访问失败转换为认证基础设施异常并保留原因。 */
  @Test
  void Redis异常失败关闭() {
    DataAccessResourceFailureException cause = new DataAccessResourceFailureException("连接失败");
    when(redisTemplate.execute(any(RedisScript.class), any(List.class))).thenThrow(cause);

    AuthenticationInfrastructureException exception =
        assertThrows(AuthenticationInfrastructureException.class, () -> store.getIfCurrent(7L));

    assertSame(cause, exception.getCause());
  }

  /** Redis 中的权限缓存 JSON 损坏时转换为认证基础设施异常。 */
  @Test
  void 损坏Json转换为认证基础设施异常() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class))).thenReturn("not-json");

    AuthenticationInfrastructureException exception =
        assertThrows(AuthenticationInfrastructureException.class, () -> store.getIfCurrent(7L));

    assertTrue(exception.getCause() instanceof RedisCacheJsonException);
  }

  /** 权限缓存序列化失败时转换为认证基础设施异常。 */
  @Test
  void 序列化失败转换为认证基础设施异常() {
    RedisCacheJsonCodec failedCodec = mock(RedisCacheJsonCodec.class);
    IllegalArgumentException cause = new IllegalArgumentException("序列化失败");
    when(failedCodec.write(any()))
        .thenThrow(new RedisCacheJsonException("Redis缓存JSON序列化失败", cause));
    store = new StringRedisPermissionStore(redisTemplate, failedCodec);
    EmployeePermissionCache cache = new EmployeePermissionCache();
    cache.setVersion(1L);
    cache.setApiPaths(Set.of("/api/orders"));
    cache.setResources(List.of());

    AuthenticationInfrastructureException exception =
        assertThrows(
            AuthenticationInfrastructureException.class, () -> store.putIfCurrent(7L, cache));

    assertSame(cause, exception.getCause().getCause());
  }

  /** Codec 抛出非受检序列化异常时也统一转换为认证基础设施异常。 */
  @Test
  void 非受检序列化异常失败关闭() {
    RedisCacheJsonCodec failedCodec = mock(RedisCacheJsonCodec.class);
    IllegalStateException cause = new IllegalStateException("序列化器状态异常");
    when(failedCodec.write(any())).thenThrow(cause);
    store = new StringRedisPermissionStore(redisTemplate, failedCodec);
    EmployeePermissionCache cache = new EmployeePermissionCache();
    cache.setVersion(1L);
    cache.setApiPaths(Set.of("/api/orders"));
    cache.setResources(List.of());

    AuthenticationInfrastructureException exception =
        assertThrows(
            AuthenticationInfrastructureException.class, () -> store.putIfCurrent(7L, cache));

    assertSame(cause, exception.getCause());
  }

  private EmployeePermissionCache permissionCacheWithResourceTree() {
    ResourceVO menu = resource(2L, 1L, ResourceType.MENU, "员工管理");
    ResourceVO directory = resource(1L, 0L, ResourceType.DIRECTORY, "系统管理");
    directory.setCreatedAt(LocalDateTime.of(2026, 7, 30, 9, 10, 11));
    directory.setChildren(List.of(menu));

    EmployeePermissionCache cache = new EmployeePermissionCache();
    cache.setVersion(3L);
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
