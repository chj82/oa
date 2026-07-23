package com.oa.service.system.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.system.cache.EmployeePermissionCache;
import java.time.Duration;
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
    store = new StringRedisPermissionStore(redisTemplate, new ObjectMapper());
  }

  /** 单次 Lua 同时读取全局版本和员工缓存，版本匹配才返回缓存。 */
  @Test
  void 读取当前版本缓存使用单次Lua() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class)))
        .thenReturn("{\"version\":3,\"apiPaths\":[\"/api/orders\"]}");

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
                    "admin:permission:invalidating")));
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
        List.of("admin:permission:version", "admin:permission:7", "admin:permission:invalidating"),
        keys.getValue());
  }

  /** 写入员工权限缓存时固定使用一天 TTL。 */
  @Test
  void 权限缓存写入一天TTL() {
    EmployeePermissionCache cache = new EmployeePermissionCache();
    cache.setVersion(3L);
    cache.setApiPaths(Set.of("/api/orders"));

    store.put(7L, cache);

    verify(valueOperations)
        .set(
            eq("admin:permission:7"),
            org.mockito.ArgumentMatchers.contains("\"version\":3"),
            eq(Duration.ofDays(1)));
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
    when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(String.class)))
        .thenReturn("token-1");
    when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("token-1")))
        .thenReturn(4L);
    when(redisTemplate.execute(any(RedisScript.class), any(List.class))).thenReturn(5L);

    String token = store.beginInvalidation();
    assertNotNull(token);
    assertEquals(4L, store.completeInvalidation(token));
    assertEquals(5L, store.retryPendingInvalidation());
  }

  /** 回滚只允许清理当前事务持有的失效 token。 */
  @Test
  void 回滚按Token清理失效标记() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("token-1")))
        .thenReturn(1L);

    store.cancelInvalidation("token-1");

    verify(redisTemplate).execute(any(RedisScript.class), any(List.class), eq("token-1"));
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

    assertTrue(exception.getCause() instanceof JsonProcessingException);
  }

  /** 权限缓存序列化失败时转换为认证基础设施异常。 */
  @Test
  void 序列化失败转换为认证基础设施异常() throws Exception {
    ObjectMapper failedObjectMapper = mock(ObjectMapper.class);
    JsonProcessingException cause = new JsonProcessingException("序列化失败") {};
    when(failedObjectMapper.writeValueAsString(any())).thenThrow(cause);
    store = new StringRedisPermissionStore(redisTemplate, failedObjectMapper);
    EmployeePermissionCache cache = new EmployeePermissionCache();
    cache.setVersion(1L);
    cache.setApiPaths(Set.of("/api/orders"));

    AuthenticationInfrastructureException exception =
        assertThrows(AuthenticationInfrastructureException.class, () -> store.put(7L, cache));

    assertSame(cause, exception.getCause());
  }

  /** Jackson 抛出非受检序列化异常时也统一转换为认证基础设施异常。 */
  @Test
  void 非受检序列化异常失败关闭() throws Exception {
    ObjectMapper failedObjectMapper = mock(ObjectMapper.class);
    IllegalStateException cause = new IllegalStateException("序列化器状态异常");
    when(failedObjectMapper.writeValueAsString(any())).thenThrow(cause);
    store = new StringRedisPermissionStore(redisTemplate, failedObjectMapper);
    EmployeePermissionCache cache = new EmployeePermissionCache();
    cache.setVersion(1L);
    cache.setApiPaths(Set.of("/api/orders"));

    AuthenticationInfrastructureException exception =
        assertThrows(AuthenticationInfrastructureException.class, () -> store.put(7L, cache));

    assertSame(cause, exception.getCause());
  }
}
