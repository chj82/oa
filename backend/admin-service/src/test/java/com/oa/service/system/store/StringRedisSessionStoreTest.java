package com.oa.service.system.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oa.common.exception.AuthenticationInfrastructureException;
import com.oa.common.model.system.cache.LoginSessionCache;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

class StringRedisSessionStoreTest {
  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> valueOperations;
  private StringRedisSessionStore store;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    store = new StringRedisSessionStore(redisTemplate, new RedisCacheJsonCodec(new ObjectMapper()));
  }

  @Test
  void 创建会话Lua脚本调用契约包含会话键索引键和TTL() {
    LoginSessionCache session = session();
    store.createSessionAtomically(
        "admin:session:hash", session, "admin:employee-sessions:7", "hash", Duration.ofDays(1));

    ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
    verify(redisTemplate)
        .execute(
            script.capture(),
            eq(List.of("admin:session:hash", "admin:employee-sessions:7")),
            argThat(
                value -> value instanceof String && ((String) value).contains("\"employeeId\":7")),
            eq(Long.toString(Duration.ofDays(1).toMillis())),
            eq("hash"));
    assertTrue(script.getValue().getScriptAsString().contains("SADD"));
    assertTrue(script.getValue().getScriptAsString().contains("PEXPIRE"));
  }

  @Test
  void 全部失效Lua脚本调用契约使用单次execute() {
    store.invalidateEmployeeSessionsAtomically("admin:employee-sessions:7", "admin:session:");

    ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
    verify(redisTemplate)
        .execute(script.capture(), eq(List.of("admin:employee-sessions:7")), eq("admin:session:"));
    assertTrue(script.getValue().getScriptAsString().contains("SMEMBERS"));
    assertTrue(script.getValue().getScriptAsString().contains("DEL"));
  }

  @Test
  void 读取续期Lua脚本调用契约包含读取存在判断和双TTL刷新() {
    when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any()))
        .thenReturn("{\"employeeId\":7,\"username\":\"user\",\"name\":\"员工\",\"superuser\":false}");

    LoginSessionCache session =
        store.getAndRefreshAtomically(
            "admin:session:hash", "admin:employee-sessions:", Duration.ofDays(1));

    assertEquals(7L, session.getEmployeeId());
    ArgumentCaptor<RedisScript<String>> script = ArgumentCaptor.forClass(RedisScript.class);
    verify(redisTemplate)
        .execute(
            script.capture(),
            eq(List.of("admin:session:hash")),
            eq("admin:employee-sessions:"),
            eq(Long.toString(Duration.ofDays(1).toMillis())));
    assertTrue(script.getValue().getScriptAsString().contains("GET"));
    assertTrue(script.getValue().getScriptAsString().contains("if not value"));
    assertEquals(2, occurrences(script.getValue().getScriptAsString(), "PEXPIRE"));
  }

  @Test
  void 删除单会话通过单次脚本同步移除索引并限制索引TTL() {
    store.removeSessionAtomically(
        "admin:session:hash", "admin:employee-sessions:7", "hash", Duration.ofDays(1));
    verify(redisTemplate)
        .execute(
            any(RedisScript.class),
            eq(List.of("admin:session:hash", "admin:employee-sessions:7")),
            eq("hash"),
            eq(Long.toString(Duration.ofDays(1).toMillis())));
  }

  @Test
  void 会话作为整体JSON字符串读取而不使用HashOperations() {
    when(valueOperations.get("admin:session:hash"))
        .thenReturn("{\"employeeId\":7,\"username\":\"user\",\"name\":\"员工\",\"superuser\":false}");
    assertEquals(7L, store.get("admin:session:hash").getEmployeeId());
    verify(redisTemplate, times(0)).opsForHash();
  }

  @Test
  void Redis访问异常包装为认证基础设施异常并保留原因() {
    DataAccessResourceFailureException cause = new DataAccessResourceFailureException("连接失败");
    when(valueOperations.get("admin:session:hash")).thenThrow(cause);

    AuthenticationInfrastructureException exception =
        assertThrows(
            AuthenticationInfrastructureException.class, () -> store.get("admin:session:hash"));

    assertSame(cause, exception.getCause());
  }

  private int occurrences(String source, String target) {
    return (source.length() - source.replace(target, "").length()) / target.length();
  }

  private LoginSessionCache session() {
    LoginSessionCache session = new LoginSessionCache();
    session.setEmployeeId(7L);
    session.setUsername("user");
    session.setName("员工");
    session.setSuperuser(false);
    return session;
  }
}
